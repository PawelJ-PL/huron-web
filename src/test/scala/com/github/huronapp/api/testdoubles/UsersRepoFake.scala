package com.github.huronapp.api.testdoubles

import cats.syntax.eq._
import com.github.huronapp.api.domain.users.UsersRepository.UsersRepository
import com.github.huronapp.api.domain.users.{Language, TemporaryToken, TokenType, User, UserAuth, UsersRepository}
import doobie.util.transactor
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.DbException
import zio.{Has, Ref, Task, ULayer, ZIO, ZLayer}

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

object UsersRepoFake {

  final case class UsersRepoState(users: Set[User] = Set.empty, auth: Set[UserAuth] = Set.empty, tokens: Set[TemporaryToken] = Set.empty)

  def create(ref: Ref[UsersRepoState]): ULayer[UsersRepository] =
    ZLayer.succeed(new UsersRepository.Service {

      override def create(user: User): ZIO[Has[transactor.Transactor[Task]], DbException, User] =
        ref
          .update { prev =>
            val updatedUsers = prev.users + user
            prev.copy(users = updatedUsers)
          }
          .as(user)

      override def getUsersNotConfirmedBefore(timestamp: Instant): ZIO[Has[transactor.Transactor[Task]], DbException, List[User]] = ???

      override def deleteUsersByIds(ids: FUUID*): ZIO[Has[transactor.Transactor[Task]], DbException, Long] = ???

      override def findById(id: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[User]] =
        ref.get.map(_.users.find(_.id === id))

      override def findByEmailDigest(emailDigest: String): ZIO[Has[transactor.Transactor[Task]], DbException, Option[User]] =
        ref.get.map(_.users.find(_.emailHash === emailDigest))

      override def updateUserData(
        userId: FUUID,
        nickName: Option[String],
        language: Option[Language]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[User]] =
        ref
          .updateAndGet { prev =>
            val user = prev.users.find(_.id === userId)
            val updatedUser = user.map { u =>
              u.copy(nickName = nickName.getOrElse(u.nickName), language = language.getOrElse(u.language))
            }
            val updated = updatedUser.map(u => prev.users.filter(_.id =!= u.id) + u).getOrElse(prev.users)
            prev.copy(users = updated)

          }
          .map(_.users.find(_.id === userId))

      override def setAuth(authData: UserAuth): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        ref
          .updateAndGet { prev =>
            val updatedAuths = prev.auth + authData
            prev.copy(auth = updatedAuths)
          }
          .flatMap(state =>
            state.users.find(_.id === authData.userId) match {
              case Some(user) => ZIO.succeed(user)
              case None       => ZIO.fail(DbException.Wrapped(new RuntimeException(s"No user related to auth $authData found")))
            }
          )

      override def updateUserAuth(auth: UserAuth): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        ref
          .getAndUpdate { prev =>
            val removed = prev.auth.filter(_.userId =!= auth.userId)
            val updated = removed + auth
            prev.copy(auth = updated)
          }
          .flatMap(stateBefore => ref.get.map(_.auth).map(auths => auths =!= stateBefore.auth))

      override def getAuthData(userId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[UserAuth]] =
        ref.get.map(_.auth.find(_.userId === userId))

      override def getUserWithAuthByEmailDigest(
        emailDigest: String
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[(User, UserAuth)]] =
        ref
          .get
          .map(state =>
            for {
              user <- state.users.find(_.emailHash === emailDigest)
              auth <- state.auth.find(_.userId === user.id)
            } yield (user, auth)
          )

      override def saveTemporaryToken(temporaryToken: TemporaryToken): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        ref.update { prev =>
          val updatedTokens = prev.tokens + temporaryToken
          prev.copy(tokens = updatedTokens)
        }

      override def getAuthWithTokenNotOlderThan(
        tokenValue: String,
        tokenType: TokenType,
        notOlderThan: FiniteDuration
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[UserAuth]] =
        ref.get.map { state =>
          val token = state.tokens.find(t => t.token === tokenValue && t.tokenType === tokenType)
          val maybeUserId = token.map(_.userId)
          maybeUserId.flatMap(userId => state.auth.find(_.userId === userId))
        }

      override def deleteTemporaryToken(token: String): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        ref
          .getAndUpdate(prev => prev.copy(tokens = prev.tokens.filter(_.token =!= token)))
          .flatMap(stateBefore => ref.get.map(_.tokens =!= stateBefore.tokens))

      override def deleteTokensByTypeAndUserId(
        tokenType: TokenType,
        userId: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Long] =
        ref
          .getAndUpdate { prev =>
            val updated = prev.tokens.filter(t => t.tokenType =!= tokenType || t.userId =!= userId)
            prev.copy(tokens = updated)
          }
          .flatMap(beforeUpdate => ref.get.map(state => beforeUpdate.tokens.size - state.tokens.size))

    })

}
