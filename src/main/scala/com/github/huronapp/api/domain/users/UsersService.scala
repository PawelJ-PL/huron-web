package com.github.huronapp.api.domain.users

import cats.syntax.eq._
import cats.syntax.show._
import com.github.huronapp.api.auth.authorization.{AuthorizationKernel, GetKeyPair, SetEncryptionKey}
import com.github.huronapp.api.auth.authorization.AuthorizationKernel.AuthorizationKernel
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.config.SecurityConfig
import com.github.huronapp.api.domain.collections.CollectionsRepository
import com.github.huronapp.api.domain.collections.CollectionsRepository.CollectionsRepository
import com.github.huronapp.api.domain.collections.dto.EncryptionKeyData
import com.github.huronapp.api.domain.users.UsersRepository.UsersRepository
import com.github.huronapp.api.domain.users.dto.fields.ContactAlias
import com.github.huronapp.api.domain.users.dto.{
  NewContactReq,
  NewPersonalApiKeyReq,
  NewUserReq,
  PasswordResetReq,
  PatchContactReq,
  PatchUserDataReq,
  UpdateApiKeyDataReq,
  UpdatePasswordReq
}
import com.github.huronapp.api.http.pagination.PaginationEnvelope
import com.github.huronapp.api.messagebus.InternalMessage
import com.github.huronapp.api.messagebus.InternalMessage.{PasswordResetRequested, UserRegistered}
import com.github.huronapp.api.utils.RandomUtils
import com.github.huronapp.api.utils.RandomUtils.RandomUtils
import com.github.huronapp.api.utils.crypto.Crypto
import com.github.huronapp.api.utils.crypto.Crypto.Crypto
import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.{MaxSize, MinSize}
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.string.Trimmed
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{Connection, Database}
import io.scalaland.chimney.dsl._
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

    def userContact(ownerId: FUUID, contactId: FUUID): ZIO[Any, Nothing, Option[UserWithContact]]

    def findUser(
      asUser: FUUID,
      nickNamePart: String Refined (Trimmed And MinSize[5]),
      limit: Refined[Int, Positive],
      drop: Int,
      includeSelf: Boolean,
      excludeContacts: Boolean
    ): ZIO[Any, Nothing, PaginationEnvelope[UserWithContact]]

    def getMultipleUsers(
      asUser: FUUID,
      userIds: Refined[List[FUUID], MaxSize[20]]
    ): ZIO[Any, Nothing, List[(FUUID, Option[UserWithContact])]]

    def createContactAs(userId: FUUID, dto: NewContactReq): ZIO[Any, CreateContactError, ContactWithUser]

    def listContactsAs(
      userId: FUUID,
      limit: Refined[Int, Positive],
      drop: Int,
      nameFilter: Option[String]
    ): ZIO[Any, Nothing, PaginationEnvelope[ContactWithUser]]

    def deleteContactAs(userId: FUUID, contactObjectId: FUUID): ZIO[Any, Nothing, Boolean]

    def patchContactAs(userId: FUUID, contactObjectId: FUUID, dto: PatchContactReq): ZIO[Any, EditContactError, ContactWithUser]

    def patchUserData(userId: FUUID, dto: PatchUserDataReq): ZIO[Any, PatchUserError, User]

    def updatePasswordForUser(userId: FUUID, dto: UpdatePasswordReq): ZIO[Any, UpdatePasswordError, Unit]

    def requestPasswordResetForUser(email: Email): ZIO[Any, RequestPasswordResetError, TemporaryToken]

    def passwordResetUsingToken(tokenValue: String, dto: PasswordResetReq): ZIO[Any, PasswordResetError, Unit]

    def createApiKeyForUser(userId: FUUID, dto: NewPersonalApiKeyReq): ZIO[Any, Nothing, ApiKey]

    def getApiKeysOf(userId: FUUID, keyType: ApiKeyType): ZIO[Any, Nothing, List[ApiKey]]

    def deleteApiKeyAs(userId: FUUID, keyId: FUUID): ZIO[Any, DeleteApiKeyError, Unit]

    def updateApiKeyAs(userId: FUUID, keyId: FUUID, dto: UpdateApiKeyDataReq): ZIO[Any, UpdateApiKeyError, ApiKey]

    def getKeyPairOfUserAs(requestorId: FUUID, keyOwner: FUUID): ZIO[Any, GetKeyPairError, Option[KeyPair]]

    def getPublicKeyOf(userId: UserId): ZIO[Any, Nothing, Option[(KeyAlgorithm, String)]]

  }

  val live: ZLayer[Crypto with UsersRepository with doobie.Database.Database with RandomUtils with Has[Logger[String]] with Has[
    Hub[InternalMessage]
  ] with Has[SecurityConfig] with KamonTracing with CollectionsRepository with AuthorizationKernel, Nothing, UsersService] =
    ZLayer
      .fromServices[Crypto.Service, UsersRepository.Service, Database.Service, RandomUtils.Service, Logger[String], Hub[
        InternalMessage
      ], SecurityConfig, KamonTracing.Service, CollectionsRepository.Service, AuthorizationKernel.Service, UsersService.Service](
        (crypto, usersRepo, db, random, logger, messageBus, securityConfig, tracing, collectionsRepo, authKernel) =>
          new Service {

            override def createUser(dto: NewUserReq): ZIO[Any, CreateUserError, User] =
              db
                .transactionOrDie(for {
                  _            <- usersRepo.findByNickName(dto.nickName.value).orDie.none.orElseFail(NickNameAlreadyRegistered(dto.nickName.value))
                  emailDigest  <- dto.email.digest.provideLayer(ZLayer.succeed(crypto))
                  _            <- usersRepo.findByEmailDigest(emailDigest).orDie.none.orElseFail(EmailAlreadyRegistered(emailDigest))
                  userId       <- random.randomFuuid
                  user = User(userId, emailDigest, dto.nickName.value, dto.language.getOrElse(Language.En))
                  passwordHash <- crypto.bcryptGenerate(dto.password.value.getBytes).orDie
                  authData = UserAuth(userId, passwordHash, confirmed = false, enabled = true)
                  keyPairId    <- random.randomFuuid
                  keyPair = dto.keyPair.into[KeyPair].withFieldConst(_.id, keyPairId).withFieldConst(_.userId, userId).transform
                  _            <- logger.info(show"Registering new user with id $userId")
                  savedUser    <- usersRepo.create(user).orDie
                  _            <- usersRepo.setAuth(authData).orDie
                  _            <- usersRepo.createKeyPair(keyPair).orDie
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

            override def userContact(ownerId: FUUID, contactId: FUUID): ZIO[Any, Nothing, Option[UserWithContact]] =
              db.transactionOrDie(usersRepo.findWithContactById(ownerId, contactId).orDie)

            override def findUser(
              asUser: FUUID,
              nickNamePart: String Refined (Trimmed And MinSize[5]),
              limit: Refined[Int, Positive],
              drop: Int,
              includeSelf: Boolean,
              excludeContacts: Boolean
            ): ZIO[Any, Nothing, PaginationEnvelope[UserWithContact]] =
              db.transactionOrDie(
                usersRepo
                  .findAllWithContactByMatchingNickname(asUser, nickNamePart.value, limit.value, drop, includeSelf, excludeContacts)
                  .orDie
              )

            override def getMultipleUsers(
              asUser: FUUID,
              userIds: Refined[List[FUUID], MaxSize[20]]
            ): ZIO[Any, Nothing, List[(FUUID, Option[UserWithContact])]] = {
              val uniqueUserIds = Set.from(userIds.value).toList
              db.transactionOrDie(
                usersRepo.getMultipleUsersWithContact(asUser, uniqueUserIds).orDie.map { result =>
                  val withUser = result.map(userWithContact => (userWithContact._1.id, Some(userWithContact)))
                  val missing = uniqueUserIds.filterNot(userId => result.map(_._1.id).contains(userId)).map(userId => (userId, None))
                  withUser ++ missing
                }
              )
            }

            override def listContactsAs(
              userId: FUUID,
              limit: Refined[Int, Positive],
              drop: Int,
              nameFilter: Option[String]
            ): ZIO[Any, Nothing, PaginationEnvelope[(UserContact, User)]] =
              db.transactionOrDie(usersRepo.getContacts(userId, limit.value, drop, nameFilter).orDie)

            private def validateUniqueAlias(
              maybeAlias: Option[ContactAlias],
              ownerId: FUUID
            ): ZIO[Connection, ContactAliasAlreadyExists, Unit] =
              maybeAlias match {
                case None        => ZIO.unit
                case Some(alias) =>
                  usersRepo.getContactByAlias(ownerId, alias.value).orDie.flatMap {
                    case None        => ZIO.unit
                    case Some(value) => ZIO.fail(ContactAliasAlreadyExists(ownerId, alias.value, value.contactId))
                  }
              }

            override def createContactAs(userId: FUUID, dto: NewContactReq): ZIO[Any, CreateContactError, ContactWithUser] =
              db.transactionOrDie(for {
                _             <- ZIO.cond(userId =!= dto.contactUserId, (), ForbiddenSelfToContacts(userId))
                _             <- validateUniqueAlias(dto.alias, userId)
                _             <- usersRepo.getContact(userId, dto.contactUserId).orDie.none.orElseFail(ContactAlreadyExists(userId, dto.contactUserId))
                contactObject <- usersRepo.findById(dto.contactUserId).orDie.someOrFail(UserNotFound(dto.contactUserId))
                contact = UserContact(userId, dto.contactUserId, dto.alias.map(_.value))
                savedContact  <- usersRepo.createContact(contact).orDie
              } yield (savedContact, contactObject))

            override def deleteContactAs(userId: FUUID, contactObjectId: FUUID): ZIO[Any, Nothing, Boolean] =
              db.transactionOrDie(usersRepo.deleteContact(userId, contactObjectId).orDie)

            override def patchContactAs(
              userId: FUUID,
              contactObjectId: FUUID,
              dto: PatchContactReq
            ): ZIO[Any, EditContactError, ContactWithUser] =
              db.transactionOrDie(for {
                _               <- ZIO.cond(dto.alias.isDefined, (), NoUpdates("contact", userId, dto))
                _               <- usersRepo.getContact(userId, contactObjectId).orDie.someOrFail(ContactNotFound(userId, contactObjectId))
                _               <- validateUniqueAlias(dto.alias.flatMap(_.value), userId)
                contactUserData <- usersRepo.findById(contactObjectId).orDie.someOrFail(ContactNotFound(userId, contactObjectId))
                _               <- usersRepo.updateContact(userId, contactObjectId, dto.alias.map(_.value.map(_.value))).orDie
                updated         <- usersRepo.getContact(userId, contactObjectId).orDie.someOrFail(ContactNotFound(userId, contactObjectId))
              } yield (updated, contactUserData))

            override def patchUserData(userId: FUUID, dto: PatchUserDataReq): ZIO[Any, PatchUserError, User] =
              db.transactionOrDie(for {
                _       <- ZIO.cond(dto.nickName.isDefined || dto.language.isDefined, (), NoUpdates("user", userId, dto))
                _       <- dto.nickName match {
                             case Some(nickname) =>
                               usersRepo.findByNickName(nickname.value).orDie.none.orElseFail(NickNameAlreadyRegistered(nickname.value))
                             case None           => ZIO.unit
                           }
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
                _                   <- checkIfUpdateContainsAllEncryptionKeys(userId, dto.collectionEncryptionKeys)
                newPasswordHash     <- crypto.bcryptGenerate(dto.newPassword.value.getBytes).orDie
                _                   <- usersRepo.updateUserAuth(authData.copy(passwordHash = newPasswordHash)).orDie
                keypair = dto.keyPair.into[KeyPair].withFieldConst(_.userId, userId).withFieldConst(_.id, FUUID.NilUUID).transform
                _                   <- logger.info(s"Updated keypair for user $userId")
                _                   <- usersRepo.updateKeypair(keypair).orDie
                _                   <- ZIO.foreachParN_(5)(dto.collectionEncryptionKeys)(keyData =>
                                         updateEncryptionKey(keyData.collectionId, userId, keyData.key, keyData.version)
                                       )
                _                   <- logger.info(s"Updated password for user $userId")
              } yield ())

            private def checkIfUpdateContainsAllEncryptionKeys(
              userId: FUUID,
              keys: List[EncryptionKeyData]
            ): ZIO[Connection, SomeEncryptionKeysMissingInUpdate, Unit] = {
              val collectionIds = keys.map(_.collectionId).toSet
              for {
                allCollectionKeysOfUser <- collectionsRepo.getAllCollectionKeysOfUser(userId).orDie
                collectionIdsWithKeyAssigned = allCollectionKeysOfUser.map(_.collectionId).toSet
                missingCollections = collectionIdsWithKeyAssigned -- collectionIds
                _                       <- ZIO.cond(missingCollections.isEmpty, (), SomeEncryptionKeysMissingInUpdate(userId, missingCollections))
              } yield ()
            }

            private def updateEncryptionKey(
              collectionId: FUUID,
              userId: FUUID,
              newKey: String,
              keyVersion: FUUID
            ): ZIO[Connection, UpdatePasswordError, Unit] =
              for {
                _                 <- authKernel.authorizeOperation(SetEncryptionKey(Subject(userId), collectionId, userId)).mapError(AuthorizationError)
                currentKeyVersion <- collectionsRepo
                                       .getCollectionDetails(collectionId)
                                       .someOrFail(new RuntimeException(s"Collection $collectionId not found"))
                                       .orDie
                                       .map(_.encryptionKeyVersion)
                _                 <-
                  ZIO.cond(currentKeyVersion === keyVersion, (), EncryptionKeyVersionMismatch(collectionId, keyVersion, currentKeyVersion))
                _                 <- collectionsRepo.updateUsersKeyForCollection(userId, collectionId, newKey, keyVersion).orDie
                _                 <- logger.info(s"Updated encryption key for collection $collectionId user $userId")
              } yield ()

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
                keyPair = dto.keyPair.into[KeyPair].withFieldConst(_.userId, userData.id).withFieldConst(_.id, FUUID.NilUUID).transform
                _                   <- collectionsRepo.deleteUserKeyFromAllCollections(userData.id).orDie
                _                   <- usersRepo.updateKeypair(keyPair).orDie
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

            override def getKeyPairOfUserAs(requestorId: FUUID, keyOwner: FUUID): ZIO[Any, GetKeyPairError, Option[KeyPair]] =
              db.transactionOrDie(for {
                _       <- authKernel.authorizeOperation(GetKeyPair(Subject(requestorId), UserId(keyOwner))).mapError(AuthorizationError)
                keyPair <- usersRepo.getKeyPairFor(keyOwner).orDie
              } yield keyPair)

            override def getPublicKeyOf(userId: UserId): ZIO[Any, Nothing, Option[(KeyAlgorithm, String)]] =
              db.transactionOrDie(usersRepo.getKeyPairFor(userId.id).orDie.map(_.map(keyPair => (keyPair.algorithm, keyPair.publicKey))))

          }
      )

}
