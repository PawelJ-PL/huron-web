package com.github.huronapp.api.testdoubles

import com.github.huronapp.api.constants.Collections
import com.github.huronapp.api.domain.collections.CollectionsService.CollectionsService
import com.github.huronapp.api.domain.collections.dto.{NewCollectionReq, NewMemberReq}
import com.github.huronapp.api.domain.collections.{
  AcceptInvitationError,
  Collection,
  CollectionId,
  CollectionMember,
  CollectionPermission,
  CollectionsService,
  DeleteCollectionError,
  EncryptionKey,
  GetCollectionDetailsError,
  GetEncryptionKeyError,
  GetMembersError,
  InviteMemberError,
  ListMemberPermissionsError,
  RemoveMemberError,
  SetMemberPermissionsError
}
import com.github.huronapp.api.domain.users.UserId
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import io.chrisdavenport.fuuid.FUUID
import zio.{ULayer, ZIO, ZLayer}

object CollectionsServiceStub extends Collections {

  final case class CollectionsServiceResponses(
    getAllCollections: ZIO[Any, Nothing, List[(Collection, Boolean)]] = ZIO.succeed(List((ExampleCollection, true))),
    getCollectionDetails: ZIO[Any, GetCollectionDetailsError, (Collection, Boolean)] = ZIO.succeed((ExampleCollection, true)),
    createCollection: ZIO[Any, Nothing, Collection] = ZIO.succeed(ExampleCollection),
    getEncryptionKey: ZIO[Any, GetEncryptionKeyError, Option[EncryptionKey]] = ZIO.some(ExampleEncryptionKey),
    getAllEncryptionKeys: ZIO[Any, Nothing, List[EncryptionKey]] = ZIO.succeed(List(ExampleEncryptionKey)),
    getMembers: ZIO[Any, GetMembersError, List[CollectionMember]] = ZIO.succeed(List(CollectionMember(CollectionId(ExampleCollectionId),
          UserId(ExampleUserId), List(CollectionPermission.ManageCollection, CollectionPermission.ReadFileMetadata)))),
    setMemberPermissions: ZIO[Any, SetMemberPermissionsError, Unit] = ZIO.unit,
    deleteCollection: ZIO[Any, DeleteCollectionError, Unit] = ZIO.unit,
    inviteMember: ZIO[Any, InviteMemberError, CollectionMember] = ZIO.succeed(CollectionMember(CollectionId(ExampleCollectionId),
        UserId(ExampleUserId), List(CollectionPermission.ReadFileMetadata))),
    deleteMember: ZIO[Any, RemoveMemberError, Unit] = ZIO.unit,
    acceptInvitation: ZIO[Any, AcceptInvitationError, Unit] = ZIO.unit,
    listPermissions: ZIO[Any, ListMemberPermissionsError, List[CollectionPermission]] =
      ZIO.succeed(List(CollectionPermission.ReadFileMetadata, CollectionPermission.ReadFile)))

  def withResponses(responses: CollectionsServiceResponses): ULayer[CollectionsService] =
    ZLayer.succeed(new CollectionsService.Service {

      override def getAllCollectionsOfUser(userId: FUUID, onlyAccepted: Boolean): ZIO[Any, Nothing, List[(Collection, Boolean)]] =
        responses.getAllCollections

      override def getCollectionDetailsAs(userId: FUUID, collectionId: FUUID): ZIO[Any, GetCollectionDetailsError, (Collection, Boolean)] =
        responses.getCollectionDetails

      override def createCollectionAs(userId: FUUID, dto: NewCollectionReq): ZIO[Any, Nothing, Collection] = responses.createCollection

      override def deleteCollectionAs(userId: UserId, collectionId: CollectionId): ZIO[Any, DeleteCollectionError, Unit] =
        responses.deleteCollection

      override def getEncryptionKeyAs(userId: FUUID, collectionId: FUUID): ZIO[Any, GetEncryptionKeyError, Option[EncryptionKey]] =
        responses.getEncryptionKey

      override def getEncryptionKeysForAllCollectionsOfUser(userId: FUUID): ZIO[Any, Nothing, List[EncryptionKey]] =
        responses.getAllEncryptionKeys

      override def inviteMemberAs(
        userId: UserId,
        collectionId: CollectionId,
        memberId: UserId,
        dto: NewMemberReq
      ): ZIO[Any, InviteMemberError, CollectionMember] = responses.inviteMember

      override def acceptInvitationAs(userId: UserId, collectionId: CollectionId): ZIO[Any, AcceptInvitationError, Unit] =
        responses.acceptInvitation

      override def getMemberPermissionsAs(
        userId: UserId,
        collectionId: CollectionId,
        memberId: UserId
      ): ZIO[Any, ListMemberPermissionsError, List[CollectionPermission]] = responses.listPermissions

      override def getCollectionMembersAs(userId: UserId, collectionId: CollectionId): ZIO[Any, GetMembersError, List[CollectionMember]] =
        responses.getMembers

      override def setMemberPermissionsAs(
        userId: UserId,
        collectionId: CollectionId,
        memberId: UserId,
        newPermissions: Refined[List[CollectionPermission], NonEmpty]
      ): ZIO[Any, SetMemberPermissionsError, Unit] = responses.setMemberPermissions

      override def deleteMemberAs(userId: UserId, collectionId: CollectionId, memberId: UserId): ZIO[Any, RemoveMemberError, Unit] =
        responses.deleteMember

    })

}
