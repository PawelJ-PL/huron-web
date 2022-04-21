package com.github.huronapp.api.testdoubles

import cats.syntax.eq._
import com.github.huronapp.api.domain.collections.{
  Collection,
  CollectionId,
  CollectionMember,
  CollectionPermission,
  CollectionsRepository,
  EncryptionKey
}
import com.github.huronapp.api.domain.collections.CollectionsRepository.CollectionsRepository
import com.github.huronapp.api.domain.users.UserId
import doobie.util.transactor
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.DbException
import zio.{Has, Ref, Task, ULayer, ZIO, ZLayer}

object CollectionsRepoFake {

  final case class UserCollection(collectionId: FUUID, userId: FUUID, accepted: Boolean)

  final case class PermissionEntry(collectionId: FUUID, userId: FUUID, permission: CollectionPermission)

  final case class CollectionsRepoState(
    collectionKeys: Set[EncryptionKey] = Set.empty,
    collections: Set[Collection] = Set.empty,
    userCollections: Set[UserCollection] = Set.empty,
    permissions: Set[PermissionEntry] = Set.empty)

  def create(ref: Ref[CollectionsRepoState]): ULayer[CollectionsRepository] =
    ZLayer.succeed(new CollectionsRepository.Service {

      override def listUsersCollections(
        userId: FUUID,
        onlyAccepted: Boolean
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[Collection]] =
        ref.get.map { state =>
          val userCollectionIds = state
            .userCollections
            .filter(_.userId === userId)
            .filter(c => c.accepted === true || c.accepted === onlyAccepted)
            .map(_.collectionId)
          state.collections.filter(c => userCollectionIds.contains(c.id)).toList
        }

      override def editUserCollection(
        userId: UserId,
        collectionId: CollectionId,
        encryptedKey: Option[Option[String]],
        keyVersion: Option[Option[FUUID]],
        accepted: Option[Boolean]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        ref.update { state =>
          val updatedUserCollection = state
            .userCollections
            .map(c =>
              if (c.userId =!= userId.id || c.collectionId =!= collectionId.id) c
              else
                c.copy(accepted = accepted.getOrElse(c.accepted))
            )
          state.copy(userCollections = updatedUserCollection)
        }

      override def getCollectionDetails(collectionId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[Collection]] =
        ref.get.map(_.collections.find(_.id === collectionId))

      override def getUserPermissionsFor(
        collectionId: FUUID,
        userId: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[CollectionPermission]] =
        ref.get.map(_.permissions.filter(e => e.collectionId === collectionId && e.userId === userId).toList.map(_.permission))

      override def getEncryptedKeyFor(
        collectionId: FUUID,
        userId: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[EncryptionKey]] =
        ref.get.map(_.collectionKeys.find(k => k.collectionId === collectionId && k.userId === userId))

      override def getAllCollectionKeysOfUser(
        userId: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[EncryptionKey]] =
        ref.get.map(_.collectionKeys.filter(_.userId === userId).toList)

      override def isCollectionAssignedToUser(
        collectionId: FUUID,
        userId: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        ref.get.map(_.userCollections.exists(c => c.collectionId === collectionId && c.userId === userId))

      override def isAcceptedBy(collectionId: FUUID, userId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[Boolean]] =
        ref.get.map(_.userCollections.find(c => c.collectionId === collectionId && c.userId === userId).map(_.accepted))

      override def createCollection(
        collection: Collection,
        creator: FUUID,
        creatorsEncryptedKey: String
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Collection] =
        ref
          .update {
            prevState =>
              if (prevState.collections.exists(_.id === collection.id))
                throw new RuntimeException(s"Duplicated collection ID ${collection.id}")
              if (prevState.userCollections.exists(_.collectionId === collection.id))
                throw new RuntimeException(s"Duplicated collection ID ${collection.id}")
              val updatedCollections = prevState.collections + collection
              val updatedUserCollections =
                prevState.userCollections + CollectionsRepoFake.UserCollection(collection.id, creator, accepted = true)
              val updatedPermissions = prevState.permissions ++ Set(
                CollectionsRepoFake.PermissionEntry(collection.id, creator, CollectionPermission.ManageCollection),
                CollectionsRepoFake.PermissionEntry(collection.id, creator, CollectionPermission.CreateFile),
                CollectionsRepoFake.PermissionEntry(collection.id, creator, CollectionPermission.ReadFile),
                CollectionsRepoFake.PermissionEntry(collection.id, creator, CollectionPermission.ReadFileMetadata)
              )
              prevState.copy(collections = updatedCollections, userCollections = updatedUserCollections, permissions = updatedPermissions)
          }
          .as(collection)

      override def deleteCollection(collectionId: CollectionId): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        ref.update { state =>
          val collections = state.collections.filterNot(_.id === collectionId.id)
          state.copy(collections = collections)
        }

      override def deleteUserKeyFromAllCollections(userId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Long] =
        ref
          .getAndUpdate { state =>
            val updated = state.collectionKeys.filter(_.userId =!= userId)
            state.copy(collectionKeys = updated)
          }
          .flatMap(before => ref.get.map(currentState => before.collectionKeys.size.toLong - currentState.collectionKeys.size.toLong))

      override def updateUsersKeyForCollection(
        userId: FUUID,
        collectionId: FUUID,
        encryptedKey: String,
        keyVersion: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        ref
          .getAndUpdate { state =>
            val filtered = state.collectionKeys.filter(c => c.collectionId =!= collectionId || c.userId =!= userId)
            val updated =
              if (filtered =!= state.collectionKeys) filtered + EncryptionKey(collectionId, userId, encryptedKey, keyVersion)
              else state.collectionKeys
            state.copy(collectionKeys = updated)

          }
          .map(prev => prev.collectionKeys.exists(c => c.collectionId === collectionId && c.userId === userId))

      override def addMember(
        collectionId: CollectionId,
        userId: UserId,
        collectionPermissions: List[CollectionPermission],
        encryptedKey: String,
        keyVersion: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        ref.update {
          state =>
            val newPermissionEntries = collectionPermissions.map(p => PermissionEntry(collectionId.id, userId.id, p))
            val updatedUserCollections =
              state.userCollections.filterNot(c => c.collectionId === collectionId.id && c.userId === userId.id) + UserCollection(
                collectionId.id,
                userId.id,
                accepted = false
              )
            val updatedPermissions =
              state.permissions.filterNot(p => p.userId === userId.id && p.collectionId === collectionId.id) ++ newPermissionEntries

            state.copy(userCollections = updatedUserCollections, permissions = updatedPermissions)

        }

      override def getMembers(collectionId: CollectionId): ZIO[Has[transactor.Transactor[Task]], DbException, List[CollectionMember]] =
        ref.get.map { state =>
          val members = state.userCollections.filter(_.collectionId === collectionId.id).map(_.userId)
          members
            .map(member =>
              CollectionMember(
                collectionId,
                UserId(member),
                state.permissions.filter(p => p.collectionId === collectionId.id && p.userId === member).map(_.permission).toList
              )
            )
            .toList
        }

      override def isMemberOf(userId: UserId, collectionId: CollectionId): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        ref
          .get
          .map(
            _.userCollections.exists(userCollection =>
              userCollection.collectionId === collectionId.id && userCollection.userId === userId.id
            )
          )

      override def setPermissions(
        collectionId: CollectionId,
        userId: UserId,
        permissions: List[CollectionPermission]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        ref.update { state =>
          val filteredPermissions =
            state.permissions.filterNot(permission => permission.userId === userId.id && permission.collectionId === collectionId.id)
          val newPermissionEntries = permissions.map(p => PermissionEntry(collectionId.id, userId.id, p))
          val finalPermissions = filteredPermissions ++ newPermissionEntries
          state.copy(permissions = finalPermissions)
        }

      override def deleteMember(collectionId: CollectionId, userId: UserId): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        ref.modify { state =>
          val updatedPermissions = state.permissions.filterNot(p => p.userId === userId.id && p.collectionId === collectionId.id)
          val updateUserCollections = state.userCollections.filterNot(c => c.collectionId === collectionId.id && c.userId === userId.id)

          val newState = state.copy(permissions = updatedPermissions, userCollections = updateUserCollections)

          (newState.userCollections.size < state.userCollections.size, newState)
        }

    })

}
