package com.github.huronapp.api.domain.users

import com.github.huronapp.api.database.BasePostgresRepository
import doobie.util.transactor
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie._
import zio.clock.Clock
import zio.{Has, Task, ZIO, ZLayer}
import io.scalaland.chimney.dsl._

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

object UsersRepository {

  type UsersRepository = Has[UsersRepository.Service]

  trait Service {

    def create(user: User): ZIO[Connection, DbException, User]

    def getUsersNotConfirmedBefore(timestamp: Instant): ZIO[Connection, DbException, List[User]]

    def deleteUsersByIds(ids: FUUID*): ZIO[Connection, DbException, Long]

    def findById(id: FUUID): ZIO[Connection, DbException, Option[User]]

    def findByEmailDigest(emailDigest: String): ZIO[Connection, DbException, Option[User]]

    def updateUserData(userId: FUUID, nickName: Option[String], language: Option[Language]): ZIO[Connection, DbException, Option[User]]

    def setAuth(authData: UserAuth): ZIO[Connection, DbException, Unit]

    def updateUserAuth(auth: UserAuth): ZIO[Connection, DbException, Boolean]

    def getAuthData(userId: FUUID): ZIO[Connection, DbException, Option[UserAuth]]

    def getUserWithAuthByEmailDigest(emailDigest: String): ZIO[Connection, DbException, Option[(User, UserAuth)]]

    def saveTemporaryToken(temporaryToken: TemporaryToken): ZIO[Connection, DbException, Unit]

    def getAuthWithTokenNotOlderThan(
      tokenValue: String,
      tokenType: TokenType,
      notOlderThan: FiniteDuration
    ): ZIO[Connection, DbException, Option[UserAuth]]

    def deleteTemporaryToken(token: String): ZIO[Connection, DbException, Boolean]

    def deleteTokensByTypeAndUserId(tokenType: TokenType, userId: FUUID): ZIO[Connection, DbException, Long]

    def createApiKey(apiKey: ApiKey): ZIO[Connection, DbException, ApiKey]

    def listUsersApiKeyWithType(userId: FUUID, apiKeyType: ApiKeyType): ZIO[Connection, DbException, List[ApiKey]]

    def getApiKey(keyId: FUUID): ZIO[Connection, DbException, Option[ApiKey]]

    def getAuthWithApiKeyByKeyValue(value: String): ZIO[Connection, DbException, Option[(UserAuth, ApiKey)]]

    def deleteApiKey(keyId: FUUID): ZIO[Connection, DbException, Boolean]

    def updateApiKey(
      keyId: FUUID,
      description: Option[String],
      enabled: Option[Boolean],
      validTo: Option[Option[Instant]]
    ): ZIO[Connection, DbException, Boolean]

  }

  val postgres: ZLayer[Clock, Nothing, UsersRepository] =
    ZLayer.fromService { clock =>
      new Service with BasePostgresRepository {
        import doobieContext._
        import dbImplicits._

        override def create(game: User): ZIO[Connection, DbException, User] =
          for {
            now <- clock.instant
            entity = game
                       .into[UserEntity]
                       .withFieldConst(_.updatedAt, now)
                       .withFieldConst(_.createdAt, now)
                       .transform
            _   <- tzio(run(quote(users.insert(lift(entity)))))
          } yield entity.transformInto[User]

        override def getUsersNotConfirmedBefore(timestamp: Instant): ZIO[Connection, DbException, List[User]] =
          tzio(
            run(
              quote(
                for {
                  auth <- authData.filter(a => a.confirmed != lift(true))
                  u    <- users.join(_.id == auth.userId)
                } yield u
              ).filter(_.createdAt < lift(timestamp))
            ).map(_.transformInto[List[User]])
          )

        override def deleteUsersByIds(ids: FUUID*): ZIO[Connection, DbException, Long] =
          tzio(
            run(
              quote(
                users.filter(user => liftQuery(ids).contains(user.id)).delete
              )
            )
          )

        override def findById(id: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[User]] =
          tzio(
            run(
              quote(
                users.filter(_.id == lift(id))
              )
            ).map(_.headOption.map(_.transformInto[User]))
          )

        override def findByEmailDigest(emailDigest: String): ZIO[Connection, DbException, Option[User]] =
          tzio {
            run(
              quote(
                users.filter(_.emailHash == lift(emailDigest))
              )
            ).map(_.headOption.map(_.transformInto[User]))
          }

        override def updateUserData(
          userId: FUUID,
          nickName: Option[String],
          language: Option[Language]
        ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[User]] =
          for {
            now     <- clock.instant
            _       <- tzio(
                         run(
                           users
                             .dynamic
                             .filter(_.id == lift(userId))
                             .update(setOpt(_.nickName, nickName), setOpt(_.language, language), setValue(_.updatedAt, now))
                         )
                       )
            updated <- tzio(run(quote(users.filter(_.id == lift(userId)))).map(_.headOption.transformInto[Option[User]]))
          } yield updated

        override def setAuth(auth: UserAuth): ZIO[Connection, DbException, Unit] =
          for {
            now <- clock.instant
            entity = auth.into[AuthEntity].withFieldConst(_.updatedAt, now).transform
            _   <- tzio(run(quote(authData.insert(lift(entity)))))
          } yield ()

        override def updateUserAuth(auth: UserAuth): ZIO[Connection, DbException, Boolean] =
          for {
            now   <- clock.instant
            entity = auth.into[AuthEntity].withFieldConst(_.updatedAt, now).transform
            count <- tzio(run(quote(authData.filter(_.userId == lift(entity.userId)).update(lift(entity)))))
          } yield count > 0

        override def getAuthData(userId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[UserAuth]] =
          tzio(
            run(
              quote(
                authData.filter(_.userId == lift(userId))
              )
            ).map(_.headOption.map(_.transformInto[UserAuth]))
          )

        override def getUserWithAuthByEmailDigest(
          emailDigest: String
        ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[(User, UserAuth)]] =
          tzio(
            run(
              quote(
                for {
                  user <- users.filter(_.emailHash == lift(emailDigest))
                  auth <- authData.join(_.userId == user.id)
                } yield (user, auth)
              )
            ).map(_.headOption).map(_.map { case (u, a) => (u.transformInto[User], a.transformInto[UserAuth]) })
          )

        override def saveTemporaryToken(temporaryToken: TemporaryToken): ZIO[Connection, DbException, Unit] =
          for {
            now <- clock.instant
            entity = temporaryToken.into[TokenEntity].withFieldConst(_.createdAt, now).transform
            _   <- tzio(run(quote(temporaryTokens.insert(lift(entity)))))
          } yield ()

        override def getAuthWithTokenNotOlderThan(
          tokenValue: String,
          tokenType: TokenType,
          notOlderThan: FiniteDuration
        ): ZIO[Connection, DbException, Option[UserAuth]] =
          for {
            now    <- clock.instant
            validTime = now.minusMillis(notOlderThan.toMillis)
            result <- tzio(run(quote(for {
                        token <- temporaryTokens.filter(t =>
                                   t.token == lift(tokenValue) && t.tokenType == lift(tokenType) && t.createdAt > lift(validTime)
                                 )
                        auth  <- authData.join(_.userId == token.userId)
                      } yield auth))).map(_.headOption.transformInto[Option[UserAuth]])
          } yield result

        override def deleteTemporaryToken(token: String): ZIO[Connection, DbException, Boolean] =
          tzio(
            run(
              quote(
                temporaryTokens.filter(_.token == lift(token)).delete
              )
            ).map(_ > 0)
          )

        override def deleteTokensByTypeAndUserId(
          tokenType: TokenType,
          userId: FUUID
        ): ZIO[Has[transactor.Transactor[Task]], DbException, Long] =
          tzio(
            run(
              quote(
                temporaryTokens.filter(t => t.tokenType == lift(tokenType) && t.userId == lift(userId)).delete
              )
            )
          )

        override def createApiKey(apiKey: ApiKey): ZIO[Has[transactor.Transactor[Task]], DbException, ApiKey] =
          for {
            now <- clock.instant
            genericKeyEntity = apiKey.into[ApiKeyEntity].withFieldConst(_.createdAt, now).withFieldConst(_.updatedAt, now).transform
            _   <- tzio(run(quote(apiKeys.insert(lift(genericKeyEntity)))))
          } yield genericKeyEntity.transformInto[ApiKey]

        override def listUsersApiKeyWithType(
          userId: FUUID,
          apiKeyType: ApiKeyType
        ): ZIO[Has[transactor.Transactor[Task]], DbException, List[ApiKey]] =
          tzio(
            run(
              quote(
                apiKeys.filter(k => k.userId == lift(userId) && k.keyType == lift(apiKeyType))
              )
            )
          ).map(_.transformInto[List[ApiKey]])

        override def getApiKey(keyId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[ApiKey]] =
          tzio(
            run(
              quote(
                apiKeys.filter(_.id == lift(keyId))
              )
            )
          ).map(_.headOption.transformInto[Option[ApiKey]])

        override def getAuthWithApiKeyByKeyValue(
          value: String
        ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[(UserAuth, ApiKey)]] =
          tzio(
            run(
              quote(
                for {
                  apiKey <- apiKeys.filter(_.key == lift(value))
                  auth   <- authData.join(_.userId == apiKey.userId)
                } yield (auth, apiKey)
              )
            )
          ).map(_.headOption.map {
            case (authEntity, apiKeyEntity) => (authEntity.transformInto[UserAuth], apiKeyEntity.transformInto[ApiKey])
          })

        override def deleteApiKey(keyId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
          tzio(
            run(
              quote(
                apiKeys.filter(_.id == lift(keyId)).delete
              )
            )
          ).map(_ < 0)

        override def updateApiKey(
          keyId: FUUID,
          description: Option[String],
          enabled: Option[Boolean],
          validTo: Option[Option[Instant]]
        ): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
          for {
            now    <- clock.instant
            result <- tzio(
                        run(
                          apiKeys
                            .dynamic
                            .filter(_.id == lift(keyId))
                            .update(
                              setOpt(_.description, description),
                              setOpt(_.enabled, enabled),
                              setOpt(_.validTo, validTo),
                              setValue(_.updatedAt, now)
                            )
                        )
                      )
          } yield result > 0

        private val users = quote {
          querySchema[UserEntity]("users")
        }

        private val authData = quote {
          querySchema[AuthEntity]("user_auth")
        }

        private val temporaryTokens = quote {
          querySchema[TokenEntity]("temporary_user_tokens")
        }

        private val apiKeys = quote {
          querySchema[ApiKeyEntity]("api_keys")
        }
      }
    }

}

private final case class UserEntity(
  id: FUUID,
  emailHash: String,
  nickName: String,
  language: Language,
  createdAt: Instant,
  updatedAt: Instant)

private final case class AuthEntity(userId: FUUID, passwordHash: String, confirmed: Boolean, enabled: Boolean, updatedAt: Instant)

private final case class TokenEntity(token: String, userId: FUUID, tokenType: TokenType, createdAt: Instant)

private final case class ApiKeyEntity(
  id: FUUID,
  key: String,
  userId: FUUID,
  keyType: ApiKeyType,
  description: String,
  enabled: Boolean,
  validTo: Option[Instant],
  createdAt: Instant,
  updatedAt: Instant)
