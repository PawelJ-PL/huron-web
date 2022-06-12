package com.github.huronapp.api.domain.collections

import cats.syntax.show._
import cats.syntax.eq._
import com.github.huronapp.api.auth.authorization.{
  AuthorizationKernel,
  DeleteCollection,
  GetCollectionDetails,
  GetCollectionMembers,
  GetEncryptionKey,
  InviteCollectionMember,
  ListMemberPermissions,
  RemoveCollectionMember,
  SetCollectionPermissions
}
import com.github.huronapp.api.auth.authorization.AuthorizationKernel.AuthorizationKernel
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.domain.collections.CollectionsRepository.CollectionsRepository
import com.github.huronapp.api.domain.collections.dto.{NewCollectionReq, NewMemberReq}
import com.github.huronapp.api.domain.files.FilesMetadataRepository
import com.github.huronapp.api.domain.files.FilesMetadataRepository.FilesMetadataRepository
import com.github.huronapp.api.domain.users.{UserId, UsersRepository}
import com.github.huronapp.api.domain.users.UsersRepository.UsersRepository
import com.github.huronapp.api.utils.RandomUtils
import com.github.huronapp.api.utils.RandomUtils.RandomUtils
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.doobie.Database
import zio.logging.{Logger, Logging}
import zio.{Has, ZIO, ZLayer}
import zio.macros.accessible

@accessible
object CollectionsService {

  type CollectionsService = Has[CollectionsService.Service]

  trait Service {

    def getAllCollectionsOfUser(userId: FUUID, onlyAccepted: Boolean): ZIO[Any, Nothing, List[(Collection, Boolean)]]

    def getCollectionDetailsAs(userId: FUUID, collectionId: FUUID): ZIO[Any, GetCollectionDetailsError, (Collection, Boolean)]

    def createCollectionAs(userId: FUUID, dto: NewCollectionReq): ZIO[Any, Nothing, Collection]

    def deleteCollectionAs(userId: UserId, collectionId: CollectionId): ZIO[Any, DeleteCollectionError, Unit]

    def getEncryptionKeyAs(userId: FUUID, collectionId: FUUID): ZIO[Any, GetEncryptionKeyError, Option[EncryptionKey]]

    def getEncryptionKeysForAllCollectionsOfUser(userId: FUUID): ZIO[Any, Nothing, List[EncryptionKey]]

    def inviteMemberAs(
      userId: UserId,
      collectionId: CollectionId,
      memberId: UserId,
      dto: NewMemberReq
    ): ZIO[Any, InviteMemberError, CollectionMember]

    def acceptInvitationAs(userId: UserId, collectionId: CollectionId): ZIO[Any, AcceptInvitationError, Unit]

    def cancelInvitationAcceptanceAs(userId: UserId, collectionId: CollectionId): ZIO[Any, CancelInvitationAcceptanceError, Unit]

    def getMemberPermissionsAs(
      userId: UserId,
      collectionId: CollectionId,
      memberId: UserId
    ): ZIO[Any, ListMemberPermissionsError, List[CollectionPermission]]

    def getCollectionMembersAs(userId: UserId, collectionId: CollectionId): ZIO[Any, GetMembersError, List[CollectionMember]]

    def setMemberPermissionsAs(
      userId: UserId,
      collectionId: CollectionId,
      memberId: UserId,
      newPermissions: Refined[List[CollectionPermission], NonEmpty]
    ): ZIO[Any, SetMemberPermissionsError, Unit]

    def deleteMemberAs(userId: UserId, collectionId: CollectionId, memberId: UserId): ZIO[Any, RemoveMemberError, Unit]

  }

  val live: ZLayer[
    Database.Database with CollectionsRepository with FilesMetadataRepository with UsersRepository with AuthorizationKernel with RandomUtils with Logging,
    Nothing,
    Has[Service]
  ] =
    ZLayer.fromServices[
      Database.Service,
      CollectionsRepository.Service,
      FilesMetadataRepository.Service,
      UsersRepository.Service,
      AuthorizationKernel.Service,
      RandomUtils.Service,
      Logger[String],
      CollectionsService.Service
    ] { (db, collectionsRepo, filesRepo, usersRepo, authKernel, random, logger) =>
      new Service {
        override def getAllCollectionsOfUser(userId: FUUID, onlyAccepted: Boolean): ZIO[Any, Nothing, List[(Collection, Boolean)]] =
          db.transactionOrDie(collectionsRepo.listUsersCollections(userId, onlyAccepted).orDie)

        override def getCollectionDetailsAs(
          userId: FUUID,
          collectionId: FUUID
        ): ZIO[Any, GetCollectionDetailsError, (Collection, Boolean)] =
          db.transactionOrDie(
            for {
              _    <- authKernel.authorizeOperation(GetCollectionDetails(Subject(userId), collectionId)).mapError(AuthorizationError)
              data <- collectionsRepo.getUserCollectionDetails(collectionId).orDie.someOrFail(CollectionNotFound(collectionId))
            } yield data
          )

        override def createCollectionAs(userId: FUUID, dto: NewCollectionReq): ZIO[Any, Nothing, Collection] =
          db.transactionOrDie(
            for {
              collectionId <- random.randomFuuid
              keyVersion   <- random.randomFuuid
              collection = Collection(collectionId, dto.name.value, keyVersion, userId)
              saved        <- collectionsRepo.createCollection(collection, userId, dto.encryptedKey.value).orDie
              _            <- logger.info(show"User $userId created collection $collectionId")
            } yield saved
          )

        override def deleteCollectionAs(userId: UserId, collectionId: CollectionId): ZIO[Any, DeleteCollectionError, Unit] =
          db.transactionOrDie(
            for {
              _       <- authKernel.authorizeOperation(DeleteCollection(Subject(userId.id), collectionId)).mapError(AuthorizationError)
              isEmpty <- filesRepo.isCollectionEmpty(collectionId).orDie
              _       <- ZIO.cond(isEmpty, (), CollectionNotEmpty(collectionId))
              _       <- logger.info(show"User $userId is going to delete collection $collectionId")
              _       <- collectionsRepo.deleteCollection(collectionId).orDie
            } yield ()
          )

        override def getEncryptionKeyAs(userId: FUUID, collectionId: FUUID): ZIO[Any, GetEncryptionKeyError, Option[EncryptionKey]] =
          db.transactionOrDie(
            for {
              _             <- authKernel.authorizeOperation(GetEncryptionKey(Subject(userId), collectionId)).mapError(AuthorizationError)
              encryptionKey <- collectionsRepo.getEncryptedKeyFor(collectionId, userId).orDie
            } yield encryptionKey
          )

        override def getEncryptionKeysForAllCollectionsOfUser(userId: FUUID): ZIO[Any, Nothing, List[EncryptionKey]] =
          db.transactionOrDie(collectionsRepo.getAllCollectionKeysOfUser(userId).orDie)

        override def inviteMemberAs(
          userId: UserId,
          collectionId: CollectionId,
          memberId: UserId,
          dto: NewMemberReq
        ): ZIO[Any, InviteMemberError, CollectionMember] =
          db.transactionOrDie(
            for {
              _               <- authKernel
                                   .authorizeOperation(InviteCollectionMember(Subject(userId.id), collectionId, memberId))
                                   .mapError(AuthorizationError)
              _               <- usersRepo.findById(memberId.id).orDie.someOrFail(UserNotFound(memberId))
              isAlreadyMember <- collectionsRepo.isMemberOf(memberId, collectionId).orDie
              _               <- ZIO.cond(!isAlreadyMember, (), AlreadyMember(memberId, collectionId))
              collection      <- collectionsRepo.getCollectionDetails(collectionId.id).orDie.someOrFail(CollectionNotFound(collectionId.id))
              _               <- ZIO.cond(
                                   collection.encryptionKeyVersion === dto.collectionKeyVersion,
                                   (),
                                   KeyVersionMismatch(collectionId, dto.collectionKeyVersion, collection.encryptionKeyVersion)
                                 )
              _               <- collectionsRepo
                                   .addMember(
                                     collectionId,
                                     memberId,
                                     dto.permissions.value,
                                     dto.encryptedCollectionKey.value,
                                     dto.collectionKeyVersion
                                   )
                                   .orDie
              _               <-
                logger.info(
                  show"User $userId invited user $memberId to collection $collectionId with permissions: ${dto.permissions.value.mkString(", ")}"
                )
            } yield CollectionMember(collectionId, memberId, dto.permissions.value)
          )

        override def acceptInvitationAs(userId: UserId, collectionId: CollectionId): ZIO[Any, AcceptInvitationError, Unit] =
          db.transactionOrDie(
            for {
              collectionAccepted <-
                collectionsRepo.isAcceptedBy(collectionId.id, userId.id).orDie.someOrFail(InvitationNotFound(collectionId, userId))
              _                  <- ZIO.cond(!collectionAccepted, (), InvitationAlreadyAccepted(collectionId, userId))
              _                  <- collectionsRepo.editUserCollection(userId, collectionId, None, None, accepted = Some(true)).orDie
              _                  <- logger.info(show"User $userId accepted invitation to collection $collectionId")
            } yield ()
          )

        override def cancelInvitationAcceptanceAs(
          userId: UserId,
          collectionId: CollectionId
        ): ZIO[Any, CancelInvitationAcceptanceError, Unit] =
          db.transactionOrDie(
            for {
              collectionAccepted <-
                collectionsRepo.isAcceptedBy(collectionId.id, userId.id).orDie.someOrFail(InvitationNotFound(collectionId, userId))
              _                  <- ZIO.cond(collectionAccepted, (), InvitationNotAccepted(collectionId, userId))
              _                  <- collectionsRepo.editUserCollection(userId, collectionId, None, None, accepted = Some(false)).orDie
              _                  <- logger.info(show"User $userId canceled invitation acceptance to collection $collectionId")
            } yield ()
          )

        override def getMemberPermissionsAs(
          userId: UserId,
          collectionId: CollectionId,
          memberId: UserId
        ): ZIO[Any, ListMemberPermissionsError, List[CollectionPermission]] =
          db.transactionOrDie(
            for {
              _           <- authKernel
                               .authorizeOperation(ListMemberPermissions(Subject(userId.id), collectionId, memberId))
                               .mapError(AuthorizationError)
              permissions <- collectionsRepo.getUserPermissionsFor(collectionId.id, memberId.id).orDie
            } yield permissions
          )

        override def getCollectionMembersAs(userId: UserId, collectionId: CollectionId): ZIO[Any, GetMembersError, List[CollectionMember]] =
          db.transactionOrDie(
            for {
              _       <- authKernel.authorizeOperation(GetCollectionMembers(Subject(userId.id), collectionId)).mapError(AuthorizationError)
              members <- collectionsRepo.getMembers(collectionId).orDie
            } yield members
          )

        override def setMemberPermissionsAs(
          userId: UserId,
          collectionId: CollectionId,
          memberId: UserId,
          newPermissions: Refined[List[CollectionPermission], NonEmpty]
        ): ZIO[Any, SetMemberPermissionsError, Unit] =
          db.transactionOrDie(
            for {
              _          <- ZIO.cond(userId =!= memberId, (), ChangeSelfPermissionsNotAllowed(userId))
              _          <- authKernel
                              .authorizeOperation(SetCollectionPermissions(Subject(userId.id), collectionId, memberId))
                              .mapError(AuthorizationError)
              isMember   <- collectionsRepo.isMemberOf(memberId, collectionId).orDie
              _          <- ZIO.cond(isMember, (), UserIsNotMemberOfCollection(memberId, collectionId))
              collection <- collectionsRepo.getCollectionDetails(collectionId.id).orDie.someOrFail(CollectionNotFound(collectionId.id))
              _          <- ZIO.cond(
                              collection.owner =!= memberId.id,
                              (),
                              ChangeCollectionOwnerPermissionsNotAllowed(userId, UserId(collection.owner), collectionId)
                            )
              _          <- collectionsRepo.setPermissions(collectionId, memberId, newPermissions.value).orDie
              _          <-
                logger.info(
                  show"User $userId changed permissions of user $memberId to collection $collectionId to: ${newPermissions.value.mkString(", ")}"
                )
            } yield ()
          )

        override def deleteMemberAs(userId: UserId, collectionId: CollectionId, memberId: UserId): ZIO[Any, RemoveMemberError, Unit] =
          db.transactionOrDie(
            for {
              _          <- authKernel
                              .authorizeOperation(RemoveCollectionMember(Subject(userId.id), collectionId, memberId))
                              .mapError(AuthorizationError)
              collection <- collectionsRepo.getCollectionDetails(collectionId.id).orDie.someOrFail(CollectionNotFound(collectionId.id))
              _          <- ZIO.cond(collection.owner =!= memberId.id, (), RemoveCollectionOwnerNotAllowed(userId, collectionId, memberId))
              removed    <- collectionsRepo.deleteMember(collectionId, memberId).orDie
              _          <- ZIO.cond(removed, (), UserIsNotMemberOfCollection(memberId, collectionId))
              _          <- logger.info(show"User $userId removed member $memberId from collection $collectionId")
            } yield ()
          )
      }
    }

}
