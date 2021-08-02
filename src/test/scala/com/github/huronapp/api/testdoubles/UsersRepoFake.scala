package com.github.huronapp.api.testdoubles

import cats.syntax.eq._
import com.github.huronapp.api.domain.users.UsersRepository.UsersRepository
import com.github.huronapp.api.domain.users.{
  ApiKey,
  ApiKeyType,
  KeyPair,
  Language,
  TemporaryToken,
  TokenType,
  User,
  UserAuth,
  UsersRepository
}
import doobie.util.transactor
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.DbException
import zio.{Has, Ref, Task, ULayer, ZIO, ZLayer}

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

object UsersRepoFake {

  final val RepoTimeNow = Instant.EPOCH.plusSeconds(1200)

  final case class UsersRepoState(
    users: Set[User] = Set.empty,
    auth: Set[UserAuth] = Set.empty,
    tokens: Set[TemporaryToken] = Set.empty,
    apiKeys: Set[ApiKey] = Set.empty,
    keyPairs: Set[KeyPair] = Set.empty)

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
          .flatMap(beforeUpdate => ref.get.map(state => (beforeUpdate.tokens.size - state.tokens.size).toLong))

      override def createApiKey(apiKey: ApiKey): ZIO[Has[transactor.Transactor[Task]], DbException, ApiKey] = {
        val savedKey = apiKey.copy(updatedAt = RepoTimeNow, createdAt = RepoTimeNow)
        ref.update(prev => prev.copy(apiKeys = prev.apiKeys + savedKey)).as(savedKey)
      }

      override def listUsersApiKeyWithType(
        userId: FUUID,
        apiKeyType: ApiKeyType
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[ApiKey]] =
        ref.get.map(_.apiKeys.filter(k => k.userId === userId && k.keyType === apiKeyType).toList)

      override def getApiKey(keyId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[ApiKey]] =
        ref.get.map(_.apiKeys.find(_.id === keyId))

      override def getAuthWithApiKeyByKeyValue(
        value: String
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[(UserAuth, ApiKey)]] =
        ref
          .get
          .map(state =>
            for {
              apiKey <- state.apiKeys.find(_.key === value)
              auth   <- state.auth.find(_.userId === apiKey.userId)
            } yield (auth, apiKey)
          )

      override def deleteApiKey(keyId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        ref
          .getAndUpdate { prev =>
            val updated = prev.apiKeys.filter(_.id =!= keyId)
            prev.copy(apiKeys = updated)
          }
          .flatMap(beforeUpdate => ref.get.map(state => state.apiKeys =!= beforeUpdate.apiKeys))

      override def updateApiKey(
        keyId: FUUID,
        description: Option[String],
        enabled: Option[Boolean],
        validTo: Option[Option[Instant]]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        ref
          .getAndUpdate { prev =>
            val apiKey = prev.apiKeys.find(_.id === keyId)
            val updatedKey = apiKey.map { k =>
              k.copy(
                description = description.getOrElse(k.description),
                enabled = enabled.getOrElse(k.enabled),
                validTo = validTo.getOrElse(k.validTo)
              )
            }
            val updated = updatedKey.map(u => prev.apiKeys.filter(_.id =!= u.id) + u).getOrElse(prev.apiKeys)
            prev.copy(apiKeys = updated)

          }
          .flatMap(beforeUpdate => ref.get.map(state => state.apiKeys =!= beforeUpdate.apiKeys))

      override def createKeyPair(keyPair: KeyPair): ZIO[Has[transactor.Transactor[Task]], DbException, KeyPair] =
        ref
          .update { prev =>
            val updatedKeyPairs = prev.keyPairs + keyPair
            prev.copy(keyPairs = updatedKeyPairs)
          }
          .as(keyPair)

      override def getKeyPairFor(userId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[KeyPair]] =
        ref.get.map(_.keyPairs.find(_.userId === userId))

      override def updateKeypair(keyPair: KeyPair): ZIO[Has[transactor.Transactor[Task]], DbException, Option[KeyPair]] =
        ref
          .getAndUpdate { prev =>
            val maybeKeyPair = prev.keyPairs.find(_.userId === keyPair.userId)
            val updatedKeyPair = maybeKeyPair.map { k =>
              k.copy(algorithm = keyPair.algorithm, publicKey = keyPair.publicKey, encryptedPrivateKey = keyPair.encryptedPrivateKey)
            }
            val updated = updatedKeyPair match {
              case Some(k) => prev.keyPairs.filter(_.userId =!= keyPair.userId) + k
              case None    => prev.keyPairs
            }
            prev.copy(keyPairs = updated)

          }
          .map(state => if (state.keyPairs.exists(_.userId === keyPair.userId)) Some(keyPair) else None)

    })

}
