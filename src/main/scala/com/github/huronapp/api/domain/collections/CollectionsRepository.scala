package com.github.huronapp.api.domain.collections

import com.github.huronapp.api.database.BasePostgresRepository
import com.github.huronapp.api.domain.users.UserId
import doobie.util.transactor
import io.chrisdavenport.fuuid.FUUID
import io.getquill.Ord
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie._
import io.scalaland.chimney.dsl._
import zio.clock.Clock
import zio.{Has, Task, ZIO, ZLayer}

import java.time.Instant

object CollectionsRepository {

  type CollectionsRepository = Has[CollectionsRepository.Service]

  trait Service {

    def listUsersCollections(userId: FUUID, onlyAccepted: Boolean): ZIO[Connection, DbException, List[Collection]]

    def editUserCollection(
      userId: UserId,
      collectionId: CollectionId,
      encryptedKey: Option[Option[String]],
      keyVersion: Option[Option[FUUID]],
      accepted: Option[Boolean]
    ): ZIO[Connection, DbException, Unit]

    def getCollectionDetails(collectionId: FUUID): ZIO[Connection, DbException, Option[Collection]]

    def getUserPermissionsFor(collectionId: FUUID, userId: FUUID): ZIO[Connection, DbException, List[CollectionPermission]]

    def getEncryptedKeyFor(collectionId: FUUID, userId: FUUID): ZIO[Connection, DbException, Option[EncryptionKey]]

    def getAllCollectionKeysOfUser(userId: FUUID): ZIO[Connection, DbException, List[EncryptionKey]]

    def isCollectionAssignedToUser(collectionId: FUUID, userId: FUUID): ZIO[Connection, DbException, Boolean]

    def isAcceptedBy(collectionId: FUUID, userId: FUUID): ZIO[Connection, DbException, Option[Boolean]]

    def createCollection(collection: Collection, creator: FUUID, creatorsEncryptedKey: String): ZIO[Connection, DbException, Collection]

    def deleteCollection(collectionId: CollectionId): ZIO[Connection, DbException, Unit]

    def deleteUserKeyFromAllCollections(userId: FUUID): ZIO[Connection, DbException, Long]

    def updateUsersKeyForCollection(
      userId: FUUID,
      collectionId: FUUID,
      encryptedKey: String,
      keyVersion: FUUID
    ): ZIO[Connection, DbException, Boolean]

    def addMember(
      collectionId: CollectionId,
      userId: UserId,
      collectionPermissions: List[CollectionPermission],
      encryptedKey: String,
      keyVersion: FUUID
    ): ZIO[Connection, DbException, Unit]

    def getMembers(collectionId: CollectionId): ZIO[Connection, DbException, List[CollectionMember]]

    def isMemberOf(userId: UserId, collectionId: CollectionId): ZIO[Connection, DbException, Boolean]

    def setPermissions(
      collectionId: CollectionId,
      userId: UserId,
      permissions: List[CollectionPermission]
    ): ZIO[Connection, DbException, Unit]

    def deleteMember(collectionId: CollectionId, userId: UserId): ZIO[Connection, DbException, Boolean]

  }

  val postgres: ZLayer[Clock, Nothing, CollectionsRepository] = ZLayer.fromService(clock =>
    new Service with BasePostgresRepository {
      import doobieContext._
      import dbImplicits._

      override def listUsersCollections(
        userId: FUUID,
        onlyAccepted: Boolean
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[Collection]] =
        tzio(
          run(
            quote(
              for {
                filtered       <- userCollections
                                    .filter(c => c.userId == lift(userId) && (c.accepted == lift(true) || c.accepted == lift(onlyAccepted)))
                                    .sortBy(_.updatedAt)(Ord.desc)
                collectionData <- collections.join(_.id == filtered.collectionId)
              } yield collectionData
            )
          ).map(_.transformInto[List[Collection]])
        )

      override def editUserCollection(
        userId: UserId,
        collectionId: CollectionId,
        encryptedKey: Option[Option[String]],
        keyVersion: Option[Option[FUUID]],
        accepted: Option[Boolean]
      ): ZIO[Connection, DbException, Unit] =
        for {
          now <- clock.instant
          _   <- tzio(
                   run(
                     userCollections
                       .dynamic
                       .filter(c => c.collectionId == lift(collectionId.id) && c.userId == lift(userId.id))
                       .update(
                         setOpt(_.encryptedKey, encryptedKey),
                         setOpt(_.keyVersion, keyVersion),
                         setOpt(_.accepted, accepted),
                         setOpt(_.updatedAt, if (encryptedKey.isDefined || keyVersion.isDefined || accepted.isDefined) Some(now) else None)
                       )
                   )
                 )
        } yield ()

      override def getCollectionDetails(collectionId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[Collection]] =
        tzio(
          run(
            quote(
              collections.filter(_.id == lift(collectionId))
            )
          ).map(_.headOption.transformInto[Option[Collection]])
        )

      override def getUserPermissionsFor(
        collectionId: FUUID,
        userId: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[CollectionPermission]] =
        tzio(
          run(
            quote(
              permissions.filter(p => p.collectionId == lift(collectionId) && p.userId == lift(userId)).map(_.permission)
            )
          )
        )

      override def getEncryptedKeyFor(
        collectionId: FUUID,
        userId: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[EncryptionKey]] =
        tzio(
          run(
            quote(
              userCollections
                .filter(c => c.collectionId == lift(collectionId) && c.userId == lift(userId))
                .map(c => (c.encryptedKey, c.keyVersion))
            )
          ).map(_.headOption.flatMap {
            case (maybeKey, maybeVersion) =>
              for {
                key     <- maybeKey
                version <- maybeVersion
              } yield EncryptionKey(collectionId, userId, key, version)
          })
        )

      override def getAllCollectionKeysOfUser(
        userId: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[EncryptionKey]] =
        tzio(
          run(
            quote(userCollections.filter(_.userId == lift(userId)).map(c => (c.collectionId, c.keyVersion, c.encryptedKey)))
              .sortBy(_._1)(Ord.asc)
          )
        ).map(_.collect {
          case (collectionId, Some(keyVersion), Some(encryptedKey)) => EncryptionKey(collectionId, userId, encryptedKey, keyVersion)
        })

      override def isCollectionAssignedToUser(
        collectionId: FUUID,
        userId: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        tzio(
          run(
            quote(
              userCollections.filter(c => c.collectionId == lift(collectionId) && c.userId == lift(userId)).size
            )
          )
        ).map(_ > 0)

      override def isAcceptedBy(collectionId: FUUID, userId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Option[Boolean]] =
        tzio(
          run(
            quote(
              userCollections.filter(c => c.collectionId == lift(collectionId) && c.userId == lift(userId)).map(_.accepted)
            )
          ).map(_.headOption)
        )

      override def createCollection(
        collection: Collection,
        creator: FUUID,
        creatorsEncryptedKey: String
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Collection] =
        for {
          now <- clock.instant
          collectionEntity = collection.into[CollectionEntity].withFieldConst(_.createdAt, now).withFieldConst(_.updatedAt, now).transform
          userCollectionEntity = UserCollectionEntity(
                                   creator,
                                   collection.id,
                                   Some(creatorsEncryptedKey),
                                   Some(collection.encryptionKeyVersion),
                                   accepted = true,
                                   now
                                 )
          permissionEntities = List(
                                 CollectionPermission.ManageCollection,
                                 CollectionPermission.CreateFile,
                                 CollectionPermission.ModifyFile,
                                 CollectionPermission.ReadFile,
                                 CollectionPermission.ReadFileMetadata
                               ).map(p => CollectionPermissionEntity(collection.id, creator, p, now))
          _   <- tzio(run(quote(collections.insert(lift(collectionEntity)))))
          _   <- tzio(run(quote(userCollections.insert(lift(userCollectionEntity)))))
          _   <- tzio(run(quote(liftQuery(permissionEntities).foreach(p => permissions.insert(p)))))
        } yield collectionEntity.transformInto[Collection]

      override def deleteCollection(collectionId: CollectionId): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        tzio(
          run(
            quote(
              collections.filter(_.id == lift(collectionId.id)).delete
            )
          )
        ).unit

      override def deleteUserKeyFromAllCollections(userId: FUUID): ZIO[Has[transactor.Transactor[Task]], DbException, Long] =
        for {
          now    <- clock.instant
          result <- tzio(
                      run(
                        quote(
                          userCollections
                            .filter(_.userId == lift(userId))
                            .update(_.keyVersion -> None, _.encryptedKey -> None, _.updatedAt -> lift(now))
                        )
                      )
                    )
        } yield result

      override def updateUsersKeyForCollection(
        userId: FUUID,
        collectionId: FUUID,
        encryptedKey: String,
        keyVersion: FUUID
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        for {
          now    <- clock.instant
          result <-
            tzio(
              run(
                quote(
                  userCollections
                    .filter(c => c.collectionId == lift(collectionId) && c.userId == lift(userId))
                    .update(_.encryptedKey -> Some(lift(encryptedKey)), _.keyVersion -> Some(lift(keyVersion)), _.updatedAt -> lift(now))
                )
              )
            )
        } yield result > 0

      override def addMember(
        collectionId: CollectionId,
        userId: UserId,
        collectionPermissions: List[CollectionPermission],
        encryptedKey: String,
        keyVersion: FUUID
      ): ZIO[Connection, DbException, Unit] =
        for {
          now <- clock.instant
          userCollectionEntity =
            UserCollectionEntity(userId.id, collectionId.id, Some(encryptedKey), Some(keyVersion), accepted = false, now)
          permissionEntities = collectionPermissions.map(p => CollectionPermissionEntity(collectionId.id, userId.id, p, now))
          _   <- tzio(run(quote(userCollections.insert(lift(userCollectionEntity)))))
          _   <- tzio(run(quote(permissions.filter(p => p.collectionId == lift(collectionId.id) && p.userId == lift(userId.id)).delete)))
          _   <- tzio(run(quote(liftQuery(permissionEntities).foreach(p => permissions.insert(p)))))
        } yield ()

      override def getMembers(collectionId: CollectionId): ZIO[Connection, DbException, List[CollectionMember]] =
        tzio(
          run(
            quote(
              for {
                userCollection       <- userCollections.filter(_.collectionId == lift(collectionId.id))
                collectionPermission <-
                  permissions.filter(_.collectionId == lift(collectionId.id)).leftJoin(_.userId == userCollection.userId)
              } yield (userCollection.userId, collectionPermission.map(_.permission))
            )
          ).map(_.groupBy(_._1).map {
            case (userId, userWithPermission) =>
              CollectionMember(collectionId, UserId(userId), userWithPermission.map(_._2).collect { case Some(permission) => permission })
          }.toList)
        )

      override def isMemberOf(userId: UserId, collectionId: CollectionId): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        tzio(
          run(
            quote(
              userCollections.filter(c => c.collectionId == lift(collectionId.id) && c.userId == lift(userId.id)).size
            )
          ).map(_ > 0)
        )

      override def setPermissions(
        collectionId: CollectionId,
        userId: UserId,
        newPermissions: List[CollectionPermission]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        for {
          now <- clock.instant
          _   <- tzio(run(quote(permissions.filter(p => p.collectionId == lift(collectionId.id) && p.userId == lift(userId.id)).delete)))
          permissionEntities = newPermissions.map(p => CollectionPermissionEntity(collectionId.id, userId.id, p, now))
          _   <- tzio(run(quote(liftQuery(permissionEntities).foreach(p => permissions.insert(p)))))
        } yield ()

      override def deleteMember(collectionId: CollectionId, userId: UserId): ZIO[Connection, DbException, Boolean] =
        for {
          _     <- tzio(run(quote(permissions.filter(p => p.collectionId == lift(collectionId.id) && p.userId == lift(userId.id)).delete)))
          count <-
            tzio(run(quote(userCollections.filter(c => c.collectionId == lift(collectionId.id) && c.userId == lift(userId.id)).delete)))
        } yield count > 0

      private val collections = quote {
        querySchema[CollectionEntity]("collections")
      }

      private val userCollections = quote {
        querySchema[UserCollectionEntity]("user_collections")
      }

      private val permissions = quote {
        querySchema[CollectionPermissionEntity]("collection_permissions")
      }

    }
  )

}

private final case class CollectionEntity(
  id: FUUID,
  name: String,
  encryptionKeyVersion: FUUID,
  owner: FUUID,
  createdAt: Instant,
  updatedAt: Instant)

private final case class UserCollectionEntity(
  userId: FUUID,
  collectionId: FUUID,
  encryptedKey: Option[String],
  keyVersion: Option[FUUID],
  accepted: Boolean,
  updatedAt: Instant)

private final case class CollectionPermissionEntity(
  collectionId: FUUID,
  userId: FUUID,
  permission: CollectionPermission,
  createdAt: Instant)
