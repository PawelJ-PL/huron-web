package com.github.huronapp.api.domain.users

import cats.syntax.eq._
import cats.syntax.show._
import com.github.huronapp.api.config.SecurityConfig
import com.github.huronapp.api.domain.users.dto.{
  NewPersonalApiKeyReq,
  NewUserReq,
  PasswordResetReq,
  PatchUserDataReq,
  UpdateApiKeyDataReq,
  UpdatePasswordReq
}
import com.github.huronapp.api.messagebus.InternalMessage
import com.github.huronapp.api.messagebus.InternalMessage.{PasswordResetRequested, UserRegistered}
import com.github.huronapp.api.utils.RandomUtils
import com.github.huronapp.api.utils.crypto.Crypto
import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{Connection, Database}
import zio.{Has, Hub, ZIO, ZLayer}
import zio.logging.Logger
import zio.macros.accessible

import java.time.Instant

@accessible
object UsersService {

  type UsersService = Has[UsersService.Service]

  trait Service {

    def createUser(dto: NewUserReq): ZIO[Any, CreateUserError, User]

    def confirmRegistration(token: String): ZIO[Any, SignUpConfirmationError, FUUID]

    def verifyCredentials(email: Email, password: String): ZIO[Any, CredentialsVerificationError, User]

    def userData(userId: FUUID): ZIO[Any, Nothing, Option[User]]

    def patchUserData(userId: FUUID, dto: PatchUserDataReq): ZIO[Any, PatchUserError, User]

    def updatePasswordForUser(userId: FUUID, dto: UpdatePasswordReq): ZIO[Any, UpdatePasswordError, Unit]

    def requestPasswordResetForUser(email: Email): ZIO[Any, RequestPasswordResetError, TemporaryToken]

    def passwordResetUsingToken(tokenValue: String, dto: PasswordResetReq): ZIO[Any, PasswordResetError, Unit]

    def createApiKeyForUser(userId: FUUID, dto: NewPersonalApiKeyReq): ZIO[Any, Nothing, ApiKey]

    def getApiKeysOf(userId: FUUID, keyType: ApiKeyType): ZIO[Any, Nothing, List[ApiKey]]

    def deleteApiKeyAs(userId: FUUID, keyId: FUUID): ZIO[Any, DeleteApiKeyError, Unit]

    def updateApiKeyAs(userId: FUUID, keyId: FUUID, dto: UpdateApiKeyDataReq): ZIO[Any, UpdateApiKeyError, ApiKey]

  }

  val live: ZLayer[Has[Crypto.Service] with Has[UsersRepository.Service] with Has[doobie.Database.Service] with Has[
    RandomUtils.Service
  ] with Has[Logger[String]] with Has[Hub[InternalMessage]] with Has[SecurityConfig] with KamonTracing, Nothing, Has[Service]] =
    ZLayer
      .fromServices[Crypto.Service, UsersRepository.Service, Database.Service, RandomUtils.Service, Logger[String], Hub[
        InternalMessage
      ], SecurityConfig, KamonTracing.Service, UsersService.Service](
        (crypto, usersRepo, db, random, logger, messageBus, securityConfig, tracing) =>
          new Service {

            override def createUser(dto: NewUserReq): ZIO[Any, CreateUserError, User] =
              db
                .transactionOrDie(for {
                  emailDigest  <- dto.email.digest.provideLayer(ZLayer.succeed(crypto))
                  _            <- usersRepo.findByEmailDigest(emailDigest).orDie.none.orElseFail(EmailAlreadyRegistered(emailDigest))
                  userId       <- random.randomFuuid
                  user = User(userId, emailDigest, dto.nickName.value, dto.language.getOrElse(Language.En))
                  passwordHash <- crypto.bcryptGenerate(dto.password.value.getBytes).orDie
                  authData = UserAuth(userId, passwordHash, confirmed = false, enabled = true)
                  _            <- logger.info(show"Registering new user with id $userId")
                  savedUser    <- usersRepo.create(user).orDie
                  _            <- usersRepo.setAuth(authData).orDie
                  randomToken  <- random.secureBytesHex(24).map(_.toLowerCase).orDie
                  registrationToken = TemporaryToken(randomToken, userId, TokenType.Registration)
                  _            <- usersRepo.saveTemporaryToken(registrationToken).orDie
                } yield (dto, savedUser, registrationToken))
                .flatMap {
                  case (dto, user, registrationToken) =>
                    tracing
                      .currentContext
                      .flatMap(ctx => messageBus.publish(UserRegistered(user, dto.email, registrationToken.token, Some(ctx))).as(user))
                }

            override def confirmRegistration(token: String): ZIO[Any, SignUpConfirmationError, FUUID] =
              db.transactionOrDie(for {
                authData <- usersRepo
                              .getAuthWithTokenNotOlderThan(token, TokenType.Registration, securityConfig.registrationValidFor)
                              .orDie
                              .someOrFail(NoValidTokenFound(token))
                _        <- ZIO.cond(authData.confirmed === false, (), RegistrationAlreadyConfirmed(authData.userId))
                updatedAuth = authData.copy(confirmed = true)
                _        <- usersRepo.updateUserAuth(updatedAuth).orDie
                _        <- usersRepo.deleteTemporaryToken(token).orDie
                _        <- logger.info(s"Account for user ${authData.userId} confirmed")
              } yield updatedAuth.userId)

            override def verifyCredentials(email: Email, password: String): ZIO[Any, CredentialsVerificationError, User] =
              for {
                emailDigest <- email.digest.provideLayer(ZLayer.succeed(crypto))
                (user, _)   <- db.transactionOrDie(verifyCredentialsWithEmailDigest(emailDigest, password))
              } yield user

            private def verifyCredentialsWithEmailDigest(
              emailDigest: String,
              password: String
            ): ZIO[Connection, CredentialsVerificationError, (User, UserAuth)] =
              (for {
                (user, authData) <- usersRepo.getUserWithAuthByEmailDigest(emailDigest).orDie.someOrFail(EmailNotFound(emailDigest))
                active           <- authData.isActive
                _                <- ZIO.cond(active, (), UserIsNotActive(user.id))
                passwordMatch    <- authData
                                      .checkPassword(password.getBytes)
                                      .provideLayer(ZLayer.succeed(crypto))
                                      .orDie
                _                <- ZIO.cond(passwordMatch, (), InvalidPassword(emailDigest))
              } yield (user, authData))
                .tapError {
                  case _: InvalidPassword => ZIO.unit
                  case _                  =>
                    crypto
                      .verifyBcryptPassword(securityConfig.dummyBcryptHash, "Something".getBytes)
                      .catchAll(err => logger.throwable("Error during testing dummy hash", err).unit)
                }

            override def userData(userId: FUUID): ZIO[Any, Nothing, Option[User]] = db.transactionOrDie(usersRepo.findById(userId).orDie)

            override def patchUserData(userId: FUUID, dto: PatchUserDataReq): ZIO[Any, PatchUserError, User] =
              db.transactionOrDie(for {
                _       <- ZIO.cond(dto.nickName.isDefined || dto.language.isDefined, (), NoUpdates("user", userId, dto))
                updated <- usersRepo.updateUserData(userId, dto.nickName.map(_.value), dto.language).orDie.someOrFail(UserNotFound(userId))
              } yield updated)

            override def updatePasswordForUser(userId: FUUID, dto: UpdatePasswordReq): ZIO[Any, UpdatePasswordError, Unit] =
              db.transactionOrDie(for {
                _                   <- ZIO.cond(dto.currentPassword =!= dto.newPassword.value, (), PasswordsEqual(userId))
                providedEmailDigest <- dto.email.digest.provideLayer(ZLayer.succeed(crypto))
                user                <- usersRepo.findById(userId).orDie.someOrFail(UserNotFound(userId))
                _                   <-
                  ZIO.cond(providedEmailDigest === user.emailHash, (), EmailDigestDoesNotMatch(userId, providedEmailDigest, user.emailHash))
                (_, authData)       <- verifyCredentialsWithEmailDigest(user.emailHash, dto.currentPassword)
                newPasswordHash     <- crypto.bcryptGenerate(dto.newPassword.value.getBytes).orDie
                _                   <- usersRepo.updateUserAuth(authData.copy(passwordHash = newPasswordHash)).orDie
                _                   <- logger.info(s"Updated password for user $userId")
              } yield ())

            override def requestPasswordResetForUser(email: Email): ZIO[Any, RequestPasswordResetError, TemporaryToken] =
              db.transactionOrDie(for {
                emailDigest  <- email.digest.provideLayer(ZLayer.succeed(crypto))
                randomToken  <- random.secureBytesHex(24).orDie
                (user, auth) <- usersRepo.getUserWithAuthByEmailDigest(emailDigest).orDie.someOrFail(EmailNotFound(emailDigest))
                isActive     <- auth.isActive
                _            <- ZIO.cond(isActive, (), UserIsNotActive(user.id))
                tempToken = TemporaryToken(randomToken, user.id, TokenType.PasswordReset)
                _            <- usersRepo.saveTemporaryToken(tempToken).orDie
                _            <- logger.info(s"Saved password reset request for user ${user.id}")
              } yield (email, user, tempToken))
                .flatMap {
                  case (email, user, token) =>
                    tracing
                      .currentContext
                      .flatMap(ctx => messageBus.publish(PasswordResetRequested(user, email, token.token, Some(ctx))).as(token))
                }

            override def passwordResetUsingToken(tokenValue: String, dto: PasswordResetReq): ZIO[Any, PasswordResetError, Unit] =
              db.transactionOrDie(for {
                userAuth            <- usersRepo
                                         .getAuthWithTokenNotOlderThan(tokenValue, TokenType.PasswordReset, securityConfig.passwordResetValidFor)
                                         .orDie
                                         .someOrFail(NoValidTokenFound(tokenValue))
                isActive            <- userAuth.isActive
                _                   <- ZIO.cond(isActive, (), UserIsNotActive(userAuth.userId))
                userData            <- usersRepo.findById(userAuth.userId).orDie.someOrFail(UserNotFound(userAuth.userId))
                expectedEmailDigest <- dto.email.digest.provideLayer(ZLayer.succeed(crypto))
                _                   <- ZIO.cond(
                                         expectedEmailDigest === userData.emailHash,
                                         (),
                                         EmailDigestDoesNotMatch(userAuth.userId, expectedEmailDigest, userData.emailHash)
                                       )
                newPasswordHash     <- crypto.bcryptGenerate(dto.password.value.getBytes).orDie
                _                   <- usersRepo.updateUserAuth(userAuth.copy(passwordHash = newPasswordHash)).orDie
                _                   <- logger.info(s"Password has been reset for user ${userAuth.userId}")
                _                   <- usersRepo.deleteTokensByTypeAndUserId(TokenType.PasswordReset, userAuth.userId).orDie
                _                   <- logger.info(s"Deleted all password reset tokens for user ${userAuth.userId}")
              } yield ())

            override def createApiKeyForUser(userId: FUUID, dto: NewPersonalApiKeyReq): ZIO[Any, Nothing, ApiKey] =
              db.transactionOrDie(for {
                keyId      <- random.randomFuuid
                keyValue   <- random.secureBytesHex(24).map(_.toLowerCase).orDie
                key = ApiKey(
                        keyId,
                        keyValue,
                        userId,
                        ApiKeyType.Personal,
                        dto.description.value,
                        enabled = true,
                        dto.validTo,
                        Instant.EPOCH,
                        Instant.EPOCH
                      )
                updatedKey <- usersRepo.createApiKey(key).orDie
                _          <- logger.info(show"Created new personal API key with id $keyId for user $userId")
              } yield updatedKey)

            override def getApiKeysOf(userId: FUUID, keyType: ApiKeyType): ZIO[Any, Nothing, List[ApiKey]] =
              db.transactionOrDie(usersRepo.listUsersApiKeyWithType(userId, keyType)).orDie

            override def deleteApiKeyAs(userId: FUUID, keyId: FUUID): ZIO[Any, DeleteApiKeyError, Unit] =
              db.transactionOrDie(for {
                apiKey <- usersRepo.getApiKey(keyId).orDie.someOrFail(ApiKeyNotFound(keyId))
                _      <- ZIO.cond(apiKey.userId === userId, (), ApiKeyBelongsToAnotherUser(keyId, userId, apiKey.userId))
                _      <- usersRepo.deleteApiKey(keyId).orDie
                _      <- logger.info(s"Deleted API key $keyId for user $userId")
              } yield ())

            override def updateApiKeyAs(userId: FUUID, keyId: FUUID, dto: UpdateApiKeyDataReq): ZIO[Any, UpdateApiKeyError, ApiKey] =
              db.transactionOrDie(for {
                _         <- ZIO.cond(
                               dto.description.isDefined || dto.enabled.isDefined || dto.validTo.isDefined,
                               (),
                               NoUpdates("API key", keyId, dto)
                             )
                apiKey    <- usersRepo.getApiKey(keyId).orDie.someOrFail(ApiKeyNotFound(keyId))
                _         <- ZIO.cond(apiKey.userId === userId, (), ApiKeyBelongsToAnotherUser(keyId, userId, apiKey.userId))
                _         <- usersRepo.updateApiKey(keyId, dto.description.map(_.value), dto.enabled, dto.validTo.map(_.value)).orDie
                savedUser <- usersRepo.getApiKey(keyId).orDie.someOrFail(ApiKeyNotFound(keyId))
                _         <- logger.info(s"Updated API key $keyId for user $userId")
              } yield savedUser)

          }
      )

}
