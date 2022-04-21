package com.github.huronapp.api.domain.collections

import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.auth.authorization._
import com.github.huronapp.api.constants.{Collections, MiscConstants, Users}
import com.github.huronapp.api.domain.collections.dto.NewMemberReq
import com.github.huronapp.api.domain.collections.dto.fields.EncryptedCollectionKey
import com.github.huronapp.api.domain.files.StorageUnit
import com.github.huronapp.api.domain.users.UserId
import com.github.huronapp.api.testdoubles.CollectionsRepoFake.{PermissionEntry, UserCollection}
import com.github.huronapp.api.testdoubles.{CollectionsRepoFake, FilesMetadataRepositoryFake, RandomUtilsStub, UsersRepoFake}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import io.github.gaelrenoux.tranzactio.doobie.Database
import zio.logging.slf4j.Slf4jLogger
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert, assertTrue}
import zio.{Has, Ref, ZLayer}

object CollectionsServiceMemberSpec extends DefaultRunnableSpec with Collections with Users with MiscConstants {

  val userId: UserId = UserId(ExampleUserId)

  val collectionId: CollectionId = CollectionId(ExampleCollectionId)

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Collections service suite")(
      getMembers,
      getMembersNoPermissions,
      setMemberPermissions,
      setSelfPermissions,
      setMemberPermissionsForbidden,
      setPermissionsForNonMember,
      setPermissionsToNonExistingCollection,
      setPermissionsForCollectionOwner,
      inviteMember,
      inviteMemberForbidden,
      inviteNonExistingMember,
      inviteAlreadyJoinedMember,
      inviteToNonExistingCollection,
      inviteWithInvalidKeyVersion,
      removeMember,
      removeSelfFromCollection,
      removeNonMemberSelfFromCollection,
      removeMemberForbidden,
      removeMemberCollectionNotFound,
      removeOwnerFromCollection,
      removeNonMember,
      acceptInvitation,
      acceptInvitationNotFound,
      acceptAlreadyAcceptedInvitation,
      listMemberPermissions,
      listSelfPermissions,
      listMemberPermissionsForbidden
    )

  private def createService(
    collectionsRepoState: Ref[CollectionsRepoFake.CollectionsRepoState],
    filesRepoState: Ref[List[StorageUnit]],
    usersRepoState: Ref[UsersRepoFake.UsersRepoState]
  ): ZLayer[TestEnvironment, Nothing, Has[CollectionsService.Service]] = {
    val collectionsRepo = CollectionsRepoFake.create(collectionsRepoState)
    val filesRepo = FilesMetadataRepositoryFake.create(filesRepoState)
    val usersRepo = UsersRepoFake.create(usersRepoState)
    val logger = Slf4jLogger.make((_, str) => str)
    Database.none ++ collectionsRepo ++ filesRepo ++ usersRepo ++ (Database.none ++ collectionsRepo >>> AuthorizationKernel.live) ++ RandomUtilsStub.create ++ logger >>> CollectionsService.live
  }

  private val inviteMemberDto = NewMemberReq(
    ExampleCollection.encryptionKeyVersion,
    EncryptedCollectionKey("abcd"),
    Refined.unsafeApply[List[CollectionPermission], NonEmpty](List(CollectionPermission.ReadFileMetadata))
  )

  private val getMembers = testM("should get members of collection") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
        UserCollection(ExampleFuuid1, ExampleUserId, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid2, accepted = true),
        UserCollection(ExampleFuuid3, ExampleFuuid4, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid4, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleFuuid1, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid2, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFile),
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile),
        PermissionEntry(ExampleCollectionId, ExampleFuuid2, CollectionPermission.ReadFileMetadata)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
      result          <-
        CollectionsService.getCollectionMembersAs(userId, collectionId).provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
    } yield assert(result)(
      hasSameElements(
        List(
          CollectionMember(
            CollectionId(ExampleCollectionId),
            UserId(ExampleFuuid4),
            List()
          ),
          CollectionMember(
            CollectionId(ExampleCollectionId),
            UserId(ExampleFuuid2),
            List(CollectionPermission.ManageCollection, CollectionPermission.ReadFileMetadata)
          ),
          CollectionMember(
            CollectionId(ExampleCollectionId),
            UserId(ExampleUserId),
            List(CollectionPermission.ModifyFile, CollectionPermission.ManageCollection, CollectionPermission.ReadFile)
          )
        )
      )
    )
  }

  private val getMembersNoPermissions = testM("should return error if user has no permissions to get members") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
        UserCollection(ExampleFuuid1, ExampleUserId, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid2, accepted = true),
        UserCollection(ExampleFuuid3, ExampleFuuid4, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid4, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile),
        PermissionEntry(ExampleFuuid1, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid2, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFile),
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile),
        PermissionEntry(ExampleCollectionId, ExampleFuuid2, CollectionPermission.ReadFileMetadata)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
      result          <- CollectionsService
                           .getCollectionMembersAs(userId, collectionId)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
    } yield assert(result)(
      isLeft(equalTo(AuthorizationError(OperationNotPermitted(GetCollectionMembers(Subject(ExampleUserId), collectionId)))))
    )
  }

  private val setMemberPermissions = testM("should set member permissions") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFile)
      )
    )

    val newPermissions =
      Refined.unsafeApply[List[CollectionPermission], NonEmpty](List(CollectionPermission.ReadFile, CollectionPermission.ModifyFile))

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
      _               <- CollectionsService
                           .setMemberPermissionsAs(
                             userId,
                             collectionId,
                             UserId(ExampleFuuid1),
                             newPermissions
                           )
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
      updatedRepo     <- collectionsRepo.get
    } yield assert(updatedRepo.permissions)(
      hasSameElements(
        Set(
          PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
          PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFile),
          PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ModifyFile)
        )
      )
    )
  }

  private val setSelfPermissions = testM("should return error if user tried to change self permissions") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFile)
      )
    )

    val newPermissions =
      Refined.unsafeApply[List[CollectionPermission], NonEmpty](List(CollectionPermission.ReadFile, CollectionPermission.ModifyFile))

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
      result          <- CollectionsService
                           .setMemberPermissionsAs(
                             UserId(ExampleFuuid1),
                             collectionId,
                             UserId(ExampleFuuid1),
                             newPermissions
                           )
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(ChangeSelfPermissionsNotAllowed(UserId(ExampleFuuid1))))) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val setMemberPermissionsForbidden = testM("should return error if user tried to change self permissions") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile),
        PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFile)
      )
    )

    val newPermissions =
      Refined.unsafeApply[List[CollectionPermission], NonEmpty](List(CollectionPermission.ReadFile, CollectionPermission.ModifyFile))

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
      result          <- CollectionsService
                           .setMemberPermissionsAs(
                             userId,
                             collectionId,
                             UserId(ExampleFuuid1),
                             newPermissions
                           )
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(
      isLeft(
        equalTo(
          AuthorizationError(
            OperationNotPermitted(
              SetCollectionPermissions(Subject(ExampleUserId), collectionId, UserId(ExampleFuuid1))
            )
          )
        )
      )
    ) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val setPermissionsForNonMember =
    testM("should return error if user tried to change permissions of user who is not member of collection") {
      val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        collections = Set(ExampleCollection),
        userCollections = Set(
          UserCollection(ExampleCollectionId, ExampleUserId, accepted = true)
        ),
        permissions = Set(
          PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
          PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFile)
        )
      )

      val newPermissions =
        Refined.unsafeApply[List[CollectionPermission], NonEmpty](List(CollectionPermission.ReadFile, CollectionPermission.ModifyFile))

      for {
        collectionsRepo <- Ref.make(initCollectionsRepoState)
        filesRepo       <- Ref.make(List.empty[StorageUnit])
        usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
        result          <- CollectionsService
                             .setMemberPermissionsAs(
                               userId,
                               collectionId,
                               UserId(ExampleFuuid1),
                               newPermissions
                             )
                             .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                             .either
        updatedRepo     <- collectionsRepo.get
      } yield assert(result)(isLeft(equalTo(UserIsNotMemberOfCollection(UserId(ExampleFuuid1), collectionId)))) &&
        assertTrue(updatedRepo == initCollectionsRepoState)
    }

  private val setPermissionsToNonExistingCollection =
    testM("should return error if user tried to change permissions to non existing collection") {
      val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        collections = Set(),
        userCollections = Set(
          UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
          UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)
        ),
        permissions = Set(
          PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
          PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFile)
        )
      )

      val newPermissions =
        Refined.unsafeApply[List[CollectionPermission], NonEmpty](List(CollectionPermission.ReadFile, CollectionPermission.ModifyFile))

      for {
        collectionsRepo <- Ref.make(initCollectionsRepoState)
        filesRepo       <- Ref.make(List.empty[StorageUnit])
        usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
        result          <- CollectionsService
                             .setMemberPermissionsAs(
                               userId,
                               collectionId,
                               UserId(ExampleFuuid1),
                               newPermissions
                             )
                             .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                             .either
        updatedRepo     <- collectionsRepo.get
      } yield assert(result)(isLeft(equalTo(CollectionNotFound(ExampleCollectionId)))) &&
        assertTrue(updatedRepo == initCollectionsRepoState)
    }

  private val setPermissionsForCollectionOwner = testM("should return error if user tried to change owners permissions") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ManageCollection)
      )
    )

    val newPermissions =
      Refined.unsafeApply[List[CollectionPermission], NonEmpty](List(CollectionPermission.ReadFile, CollectionPermission.ModifyFile))

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
      result          <- CollectionsService
                           .setMemberPermissionsAs(
                             UserId(ExampleFuuid1),
                             collectionId,
                             userId,
                             newPermissions
                           )
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(ChangeCollectionOwnerPermissionsNotAllowed(UserId(ExampleFuuid1), userId, collectionId)))) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val inviteMember = testM("should invite a new member") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users =
      Set(
        ExampleUser,
        ExampleUser.copy(id = ExampleFuuid1)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      _               <- CollectionsService
                           .inviteMemberAs(userId, collectionId, UserId(ExampleFuuid1), inviteMemberDto)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
      updatedRepo     <- collectionsRepo.get
    } yield assertTrue(updatedRepo.userCollections == Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = false))) &&
      assertTrue(
        updatedRepo.permissions == Set(
          PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
          PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFileMetadata)
        )
      )
  }

  private val inviteMemberForbidden = testM("should return error if user has no sufficient permissions to invite member") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users =
      Set(
        ExampleUser,
        ExampleUser.copy(id = ExampleFuuid1)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .inviteMemberAs(userId, collectionId, UserId(ExampleFuuid1), inviteMemberDto)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(
      isLeft(
        equalTo(
          AuthorizationError(
            OperationNotPermitted(InviteCollectionMember(Subject(ExampleUserId), collectionId, UserId(ExampleFuuid1)))
          )
        )
      )
    ) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val inviteNonExistingMember = testM("should return error if user not exists") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser))

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .inviteMemberAs(userId, collectionId, UserId(ExampleFuuid1), inviteMemberDto)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(UserNotFound(UserId(ExampleFuuid1))))) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val inviteAlreadyJoinedMember = testM("should return error if invited member who already joined") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = false)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users =
      Set(
        ExampleUser,
        ExampleUser.copy(id = ExampleFuuid1)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .inviteMemberAs(userId, collectionId, UserId(ExampleFuuid1), inviteMemberDto)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(AlreadyMember(UserId(ExampleFuuid1), collectionId)))) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val inviteToNonExistingCollection = testM("should return error when inviting to non existing collection") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(),
      userCollections = Set(),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users =
      Set(
        ExampleUser,
        ExampleUser.copy(id = ExampleFuuid1)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .inviteMemberAs(userId, collectionId, UserId(ExampleFuuid1), inviteMemberDto)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(CollectionNotFound(ExampleCollectionId)))) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val inviteWithInvalidKeyVersion = testM("should return error if invited with non valid key version") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users =
      Set(
        ExampleUser,
        ExampleUser.copy(id = ExampleFuuid1)
      )
    )

    val dto = inviteMemberDto.copy(collectionKeyVersion = ExampleFuuid2)

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .inviteMemberAs(userId, collectionId, UserId(ExampleFuuid1), dto)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(KeyVersionMismatch(collectionId, ExampleFuuid2, ExampleCollection.encryptionKeyVersion)))) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val removeMember = testM("should remove collection member") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFile)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users =
      Set(
        ExampleUser,
        ExampleUser.copy(id = ExampleFuuid1)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      _               <- CollectionsService
                           .deleteMemberAs(userId, collectionId, UserId(ExampleFuuid1))
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
      updatedRepo     <- collectionsRepo.get
    } yield assertTrue(updatedRepo.userCollections == Set(UserCollection(ExampleCollectionId, ExampleUserId, accepted = true))) &&
      assertTrue(
        updatedRepo.permissions == Set(
          PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection)
        )
      )
  }

  private val removeSelfFromCollection = testM("should remove self from collection members") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFile)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users =
      Set(
        ExampleUser,
        ExampleUser.copy(id = ExampleFuuid1)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      _               <- CollectionsService
                           .deleteMemberAs(UserId(ExampleFuuid1), collectionId, UserId(ExampleFuuid1))
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
      updatedRepo     <- collectionsRepo.get
    } yield assertTrue(updatedRepo.userCollections == Set(UserCollection(ExampleCollectionId, ExampleUserId, accepted = true))) &&
      assertTrue(
        updatedRepo.permissions == Set(
          PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection)
        )
      )
  }

  private val removeNonMemberSelfFromCollection =
    testM("should return error if removing self from collection which user is not member of") {
      val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        collections = Set(ExampleCollection),
        userCollections = Set(
          UserCollection(ExampleCollectionId, ExampleUserId, accepted = true)
        ),
        permissions = Set(
          PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection)
        )
      )
      val usersRepoState = UsersRepoFake.UsersRepoState(users =
        Set(
          ExampleUser,
          ExampleUser.copy(id = ExampleFuuid1)
        )
      )

      for {
        collectionsRepo <- Ref.make(initCollectionsRepoState)
        filesRepo       <- Ref.make(List.empty[StorageUnit])
        usersRepo       <- Ref.make(usersRepoState)
        result          <- CollectionsService
                             .deleteMemberAs(UserId(ExampleFuuid1), collectionId, UserId(ExampleFuuid1))
                             .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                             .either
        updatedRepo     <- collectionsRepo.get
      } yield assert(result)(
        isLeft(
          equalTo(
            AuthorizationError(OperationNotPermitted(RemoveCollectionMember(Subject(ExampleFuuid1), collectionId, UserId(ExampleFuuid1))))
          )
        )
      ) &&
        assertTrue(updatedRepo == initCollectionsRepoState)
    }

  private val removeMemberForbidden = testM("should return error if user has no sufficient permissions to remove member") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFile)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users =
      Set(
        ExampleUser,
        ExampleUser.copy(id = ExampleFuuid1)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .deleteMemberAs(UserId(ExampleFuuid1), collectionId, UserId(ExampleUserId))
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(
      isLeft(
        equalTo(
          AuthorizationError(OperationNotPermitted(RemoveCollectionMember(Subject(ExampleFuuid1), collectionId, UserId(ExampleUserId))))
        )
      )
    ) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val removeMemberCollectionNotFound = testM("should return error when removing member from non existing collection") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(),
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFile)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users =
      Set(
        ExampleUser,
        ExampleUser.copy(id = ExampleFuuid1)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .deleteMemberAs(userId, collectionId, UserId(ExampleFuuid1))
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(CollectionNotFound(ExampleCollectionId)))) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val removeOwnerFromCollection = testM("should return error when removing collection owner") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true),
        UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ManageCollection)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users =
      Set(
        ExampleUser,
        ExampleUser.copy(id = ExampleFuuid1)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .deleteMemberAs(UserId(ExampleFuuid1), collectionId, userId)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(RemoveCollectionOwnerNotAllowed(UserId(ExampleFuuid1), collectionId, userId)))) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val removeNonMember = testM("should return error when removing user who is not collection member") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true)
      ),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState(users =
      Set(
        ExampleUser,
        ExampleUser.copy(id = ExampleFuuid1)
      )
    )

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .deleteMemberAs(userId, collectionId, UserId(ExampleFuuid1))
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(UserIsNotMemberOfCollection(UserId(ExampleFuuid1), collectionId)))) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val acceptInvitation = testM("should accept invitation") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = false)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState()

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      _               <- CollectionsService
                           .acceptInvitationAs(userId, collectionId)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
      updatedRepo     <- collectionsRepo.get
    } yield assertTrue(updatedRepo.userCollections == Set(UserCollection(ExampleCollectionId, ExampleUserId, accepted = true)))
  }

  private val acceptInvitationNotFound = testM("should return error when accepted invitation which not exists") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set()
    )
    val usersRepoState = UsersRepoFake.UsersRepoState()

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .acceptInvitationAs(userId, collectionId)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(InvitationNotFound(collectionId, userId)))) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val acceptAlreadyAcceptedInvitation = testM("should return error when accepted invitation which is already accepted") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(
        UserCollection(ExampleCollectionId, ExampleUserId, accepted = true)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState()

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .acceptInvitationAs(userId, collectionId)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
      updatedRepo     <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(InvitationAlreadyAccepted(collectionId, userId)))) &&
      assertTrue(updatedRepo == initCollectionsRepoState)
  }

  private val listMemberPermissions = testM("should list member permissions") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFileMetadata)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState()

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .getMemberPermissionsAs(userId, collectionId, UserId(ExampleFuuid1))
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
    } yield assert(result)(hasSameElements(List(CollectionPermission.ReadFileMetadata)))
  }

  private val listSelfPermissions = testM("should list self permissions") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
        PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFileMetadata)
      )
    )
    val usersRepoState = UsersRepoFake.UsersRepoState()

    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(usersRepoState)
      result          <- CollectionsService
                           .getMemberPermissionsAs(UserId(ExampleFuuid1), collectionId, UserId(ExampleFuuid1))
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
    } yield assert(result)(hasSameElements(List(CollectionPermission.ReadFileMetadata)))
  }

  private val listMemberPermissionsForbidden =
    testM("should return error when listing permissions if user has no rights to perform this action") {
      val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        permissions = Set(
          PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection),
          PermissionEntry(ExampleCollectionId, ExampleFuuid1, CollectionPermission.ReadFileMetadata)
        )
      )
      val usersRepoState = UsersRepoFake.UsersRepoState()

      for {
        collectionsRepo <- Ref.make(initCollectionsRepoState)
        filesRepo       <- Ref.make(List.empty[StorageUnit])
        usersRepo       <- Ref.make(usersRepoState)
        result          <- CollectionsService
                             .getMemberPermissionsAs(UserId(ExampleFuuid1), collectionId, userId)
                             .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                             .either
      } yield assert(result)(
        isLeft(equalTo(AuthorizationError(OperationNotPermitted(ListMemberPermissions(Subject(ExampleFuuid1), collectionId, userId)))))
      )
    }

}
