package com.github.huronapp.api.domain.users

import com.github.huronapp.api.database.BasePostgresRepository
import com.github.huronapp.api.http.pagination.PaginationEnvelope
import doobie.util.transactor
import io.chrisdavenport.fuuid.FUUID
import io.getquill.Ord
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie._
import io.scalaland.chimney.Transformer
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

    def findByNickName(nickName: String): ZIO[Connection, DbException, Option[User]]

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

    def findWithContactById(ownerId: FUUID, contactId: FUUID): ZIO[Connection, DbException, Option[UserWithContact]]

    def findAllWithContactByMatchingNickname(
      ownerId: FUUID,
      matchingNickName: String,
      limit: Int,
      drop: Int,
      includeSelf: Boolean
    ): ZIO[Connection, DbException, PaginationEnvelope[UserWithContact]]

    def getContacts(ownerId: FUUID, limit: Int, drop: Int): ZIO[Connection, DbException, PaginationEnvelope[ContactWithUser]]

    def getContact(ownerId: FUUID, objectId: FUUID): ZIO[Connection, DbException, Option[UserContact]]

    def getContactByAlias(ownerId: FUUID, alias: String): ZIO[Connection, DbException, Option[UserContact]]

    def createContact(contact: UserContact): ZIO[Connection, DbException, UserContact]

    def deleteContact(ownerId: FUUID, contactId: FUUID): ZIO[Connection, DbException, Boolean]

    def updateContact(ownerId: FUUID, contactId: FUUID, alias: Option[Option[String]]): ZIO[Connection, DbException, Boolean]

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

    def createKeyPair(keyPair: KeyPair): ZIO[Connection, DbException, KeyPair]

    def getKeyPairFor(userId: FUUID): ZIO[Connection, DbException, Option[KeyPair]]

    def updateKeypair(keyPair: KeyPair): ZIO[Connection, DbException, Option[KeyPair]]

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

        override def findByNickName(nickName: String): ZIO[Connection, DbException, Option[User]] =
          tzio {
            run(
              quote(
                users.filter(_.nickName.toLowerCase == lift(nickName).toLowerCase)
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

        override def findWithContactById(
          ownerId: FUUID,
          contactId: FUUID
        ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[(User, Option[UserContact])]] =
          tzio(
            run(
              quote(
                for {
                  user    <- users.filter(_.id == lift(contactId))
                  contact <- contacts.filter(_.contactOwnerId == lift(ownerId)).leftJoin(_.contactObjectId == user.id)
                } yield (user, contact)
              )
            ).map(_.headOption.transformInto[Option[(User, Option[UserContact])]])
          )

        private def filterMatchingUsers(ownerId: FUUID, matchingNickName: String, includeSelf: Boolean) =
          quote(
            users.filter(u =>
              (u.nickName.toLowerCase like lift(matchingNickName.toLowerCase + "%")) && (u.id != lift(ownerId) || lift(includeSelf))
            )
          )

        override def findAllWithContactByMatchingNickname(
          ownerId: FUUID,
          matchingNickName: String,
          limit: Int,
          drop: Int,
          includeSelf: Boolean
        ): ZIO[Connection, DbException, PaginationEnvelope[UserWithContact]] =
          for {
            rows  <- tzio(
                       run(
                         quote(
                           for {
                             user         <- filterMatchingUsers(ownerId, matchingNickName, includeSelf)
                             maybeContact <- contacts.filter(_.contactOwnerId == lift(ownerId)).leftJoin(_.contactObjectId == user.id)
                           } yield (user, maybeContact)
                         )
                           .sortBy(result => (result._1.nickName.sqlLength, result._1.nickName))(Ord(Ord.asc, Ord.asc))
                           .drop(lift(drop))
                           .take(lift(limit))
                       ).map(_.transformInto[List[(User, Option[UserContact])]])
                     )
            total <- tzio(run(quote(filterMatchingUsers(ownerId, matchingNickName, includeSelf).size)))
          } yield PaginationEnvelope(rows, total)

        override def getContacts(
          ownerId: FUUID,
          limit: Index,
          drop: Index
        ): ZIO[Connection, DbException, PaginationEnvelope[ContactWithUser]] =
          for {
            rows  <- tzio(
                       run(
                         quote(for {
                           contact <- contacts.filter(_.contactOwnerId == lift(ownerId))
                           user    <- users.join(_.id == contact.contactObjectId)
                         } yield (contact, user))
                           .sortBy(result => (result._1.alias, result._2.nickName))(Ord(Ord.ascNullsLast, Ord.asc))
                           .drop(lift(drop))
                           .take(lift(limit))
                       ).map(_.transformInto[List[ContactWithUser]])
                     )
            total <- tzio(run(quote(contacts.filter(_.contactOwnerId == lift(ownerId)).size)))
          } yield PaginationEnvelope(rows, total)

        override def getContact(ownerId: FUUID, objectId: FUUID): ZIO[Connection, DbException, Option[UserContact]] =
          tzio(
            run(
              quote(
                contacts.filter(c => c.contactOwnerId == lift(ownerId) && c.contactObjectId == lift(objectId))
              )
            ).map(_.headOption.transformInto[Option[UserContact]])
          )

        override def getContactByAlias(
          ownerId: FUUID,
          alias: String
        ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[UserContact]] =
          tzio(
            run(
              quote(
                contacts.filter(c => c.contactOwnerId == lift(ownerId) && c.alias.contains(lift(alias)))
              )
            ).map(_.headOption.transformInto[Option[UserContact]])
          )

        override def createContact(contact: UserContact): ZIO[Has[transactor.Transactor[Task]], DbException, UserContact] =
          for {
            now <- clock.instant
            contactEntity = UserContactEntity(contact.ownerId, contact.contactId, contact.alias, now, now)
            _   <- tzio(run(quote(contacts.insert(lift(contactEntity)))))
          } yield contact

        override def deleteContact(ownerId: FUUID, contactId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
          tzio(
            run(
              quote(
                contacts.filter(c => c.contactOwnerId == lift(ownerId) && c.contactObjectId == lift(contactId)).delete
              )
            )
          ).map(_ > 0)

        override def updateContact(
          ownerId: FUUID,
          contactId: FUUID,
          alias: Option[Option[String]]
        ): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
          for {
            now    <- clock.instant
            result <- tzio(
                        run(
                          contacts
                            .dynamic
                            .filter(c => c.contactOwnerId == lift(ownerId) && c.contactObjectId == lift(contactId))
                            .update(
                              setOpt(_.alias, alias),
                              setValue(_.updatedAt, now)
                            )
                        )
                      )
          } yield result > 0

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
                apiKeys.filter(k => k.userId == lift(userId) && k.keyType == lift(apiKeyType)).sortBy(_.createdAt)(Ord.desc)
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
          ).map(_ > 0)

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

        override def createKeyPair(keyPair: KeyPair): ZIO[Has[transactor.Transactor[Task]], DbException, KeyPair] =
          for {
            now <- clock.instant
            keyPairEntity = keyPair.into[KeyPairEntity].withFieldConst(_.updatedAt, now).transform
            _   <- tzio(run(quote(keyPairs.insert(lift(keyPairEntity)))))
          } yield keyPairEntity.transformInto[KeyPair]

        override def getKeyPairFor(userId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[KeyPair]] =
          tzio(
            run(
              quote(
                keyPairs.filter(_.userId == lift(userId))
              )
            ).map(_.headOption.transformInto[Option[KeyPair]])
          )

        override def updateKeypair(keyPair: KeyPair): ZIO[Has[transactor.Transactor[Task]], DbException, Option[KeyPair]] =
          for {
            now   <- clock.instant
            entity = keyPair.into[KeyPairEntity].withFieldConst(_.updatedAt, now).transform
            count <- tzio(run(quote(keyPairs.filter(_.userId == lift(entity.userId)).update(lift(entity)))))
          } yield if (count > 0) Some(entity.transformInto[KeyPair]) else None

        @annotation.nowarn("cat=unused")
        private implicit val keyPairUpdateMeta = updateMeta[KeyPairEntity](_.id, _.userId)

        private val users = quote {
          querySchema[UserEntity]("users")
        }

        private val authData = quote {
          querySchema[AuthEntity]("user_auth")
        }

        private val temporaryTokens = quote {
          querySchema[TokenEntity]("temporary_user_tokens")
        }

        private val contacts = quote {
          querySchema[UserContactEntity]("user_contacts")
        }

        private val apiKeys = quote {
          querySchema[ApiKeyEntity]("api_keys")
        }

        private val keyPairs = quote {
          querySchema[KeyPairEntity]("user_keypairs")
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

private final case class KeyPairEntity(
  id: FUUID,
  userId: FUUID,
  algorithm: KeyAlgorithm,
  publicKey: String,
  encryptedPrivateKey: String,
  updatedAt: Instant)

private final case class UserContactEntity(
  contactOwnerId: FUUID,
  contactObjectId: FUUID,
  alias: Option[String],
  createdAt: Instant,
  updatedAt: Instant)

private object UserContactEntity {

  implicit val toDomainTransformer: Transformer[UserContactEntity, UserContact] = Transformer
    .define[UserContactEntity, UserContact]
    .withFieldComputed(_.contactId, _.contactObjectId)
    .withFieldComputed(_.ownerId, _.contactOwnerId)
    .buildTransformer

}
