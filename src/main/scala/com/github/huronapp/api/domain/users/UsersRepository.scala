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

        private val users = quote {
          querySchema[UserEntity]("users")
        }

        private val authData = quote {
          querySchema[AuthEntity]("user_auth")
        }

        private val temporaryTokens = quote {
          querySchema[TokenEntity]("temporary_user_tokens")
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

private final case class AuthEntity(userId: FUUID, passwordHash: Option[String], confirmed: Boolean, enabled: Boolean, updatedAt: Instant)

private final case class TokenEntity(token: String, userId: FUUID, tokenType: TokenType, createdAt: Instant)
