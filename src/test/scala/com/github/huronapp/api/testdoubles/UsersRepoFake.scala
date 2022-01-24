package com.github.huronapp.api.testdoubles

import cats.syntax.eq._
import com.github.huronapp.api.domain.users.UsersRepository.UsersRepository
import com.github.huronapp.api.domain.users.{
  ApiKey,
  ApiKeyType,
  ContactWithUser,
  KeyPair,
  Language,
  TemporaryToken,
  TokenType,
  User,
  UserAuth,
  UserContact,
  UserWithContact,
  UsersRepository
}
import com.github.huronapp.api.http.pagination.PaginationEnvelope
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
    contacts: Set[UserContact] = Set.empty,
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

      override def findByNickName(nickName: String): ZIO[Has[transactor.Transactor[Task]], DbException, Option[User]] =
        ref.get.map(_.users.find(_.nickName === nickName))

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
          .unit

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

      override def findWithContactById(
        ownerId: FUUID,
        contactId: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[(User, Option[UserContact])]] =
        ref.get.map { state =>
          state.users.find(_.id === contactId).map { user =>
            val contact = state.contacts.find(c => c.contactId === user.id && c.ownerId === ownerId)
            (user, contact)
          }
        }

      override def findAllWithContactByMatchingNickname(
        ownerId: FUUID,
        matchingNickName: String,
        limit: Int,
        drop: Int,
        includeSelf: Boolean,
        excludeContacts: Boolean
      ): ZIO[Has[transactor.Transactor[Task]], DbException, PaginationEnvelope[UserWithContact]] =
        ref.get.map {
          state =>
            val usersFilter: User => Boolean = u => u.nickName.startsWith(matchingNickName) && (u.id =!= ownerId || includeSelf)

            val matchingUsers =
              state
                .users
                .filter(usersFilter)
                .toList
                .sortBy(u => (u.nickName.length, u.nickName))
                .slice(drop, drop + limit)
            val data = matchingUsers.map { u =>
              val maybeContact = state.contacts.find(c => c.ownerId === ownerId && c.contactId === u.id)
              (u, maybeContact)
            }
            val filteredData = data.filter { case (_, maybeContact) => !excludeContacts || maybeContact.isEmpty }
            val total = state.users.count(usersFilter)
            PaginationEnvelope(filteredData, total.toLong)
        }

      override def getMultipleUsersWithContact(
        owner: FUUID,
        userIds: List[FUUID]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[(User, Option[UserContact])]] =
        ref.get.map { state =>
          val users = state.users.filter(u => userIds.contains(u.id))
          users.map { u =>
            val maybeContact = state.contacts.find(c => c.ownerId === owner && c.contactId === u.id)
            (u, maybeContact)
          }.toList
        }

      override def getContacts(
        ownerId: FUUID,
        limit: Int,
        drop: Int,
        nameFilter: Option[String]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, PaginationEnvelope[ContactWithUser]] =
        ref.get.map {
          state =>
            val contacts = state.contacts.filter(_.ownerId === ownerId)
            val pairs = contacts.map(c => (c, state.users.find(_.id === c.contactId))).collect {
              case (contact, Some(u)) => (contact, u)
            }
            val result = pairs
              .toList
              .sortWith { (prev, next) =>
                import math.Ordered._
                if (prev._1.alias.isEmpty && next._1.alias.isEmpty)
                  prev._2.nickName < next._2.nickName
                else if (prev._1.alias.isDefined && next._1.alias.isDefined)
                  prev._1.alias < next._1.alias
                else if (prev._1.alias.isDefined && next._1.alias.isEmpty)
                  true
                else false
              }
              .slice(drop, drop + limit)
            val filteredResult = nameFilter match {
              case Some(filter) =>
                result.filter {
                  case (contact, user) =>
                    contact.alias.exists(c => c.toLowerCase.contains(filter.toLowerCase)) ||
                      user.nickName.toLowerCase.contains(filter.toLowerCase)
                }
              case None         => result
            }
            PaginationEnvelope(filteredResult, pairs.size.toLong)
        }

      override def getContact(ownerId: FUUID, objectId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[UserContact]] =
        ref.get.map(_.contacts.find(c => c.ownerId === ownerId && c.contactId === objectId))

      override def getContactByAlias(
        ownerId: FUUID,
        alias: String
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[UserContact]] =
        ref.get.map(_.contacts.find(c => c.alias.contains(alias) && c.ownerId === ownerId))

      override def createContact(contact: UserContact): ZIO[Has[transactor.Transactor[Task]], DbException, UserContact] =
        ref
          .update { state =>
            val updated = state.contacts + contact
            state.copy(contacts = updated)
          }
          .as(contact)

      override def deleteContact(ownerId: FUUID, contactId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] = ???

      override def updateContact(
        ownerId: FUUID,
        contactId: FUUID,
        alias: Option[Option[String]]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        ref.modify { state =>
          val maybeUpdated =
            state.contacts.find(c => c.ownerId === ownerId && c.contactId === contactId).map(c => c.copy(alias = alias.getOrElse(c.alias)))
          val newContacts = maybeUpdated
            .map(contact => state.contacts.filter(c => c.contactId =!= contact.contactId || c.ownerId =!= contact.ownerId) + contact)
            .getOrElse[Set[UserContact]](state.contacts)
          (maybeUpdated.isDefined, state.copy(contacts = newContacts))
        }

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
