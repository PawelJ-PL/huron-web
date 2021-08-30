package com.github.huronapp.api.auth.authorization

import com.github.huronapp.api.auth.authorization.AuthorizationKernel.AuthorizationKernel
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.constants.{Collections, MiscConstants, Users}
import com.github.huronapp.api.domain.collections.{CollectionId, CollectionPermission}
import com.github.huronapp.api.testdoubles.CollectionsRepoFake
import com.github.huronapp.api.testdoubles.CollectionsRepoFake.{PermissionEntry, UserCollection}
import io.github.gaelrenoux.tranzactio.doobie.Database
import zio.{Ref, ZLayer}
import zio.test.Assertion.{equalTo, isLeft, isRight, isUnit}
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

object AuthorizationKernelSpec extends DefaultRunnableSpec with Users with Collections with MiscConstants {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Authorization kernel suite")(
      getCollectionDetails,
      getCollectionDetailsIfCollectionNotAssignedToUser,
      setEncryptionKeyByKeyOwner,
      setEncryptionKeyByCollectionAdmin,
      setEncryptionKeyIfCollectionNotAssigned,
      getEncryptionKey,
      getEncryptionKeyIfCollectionNotAssignedToUser,
      createFile,
      createFileWithoutCreatePermission,
      createFileWithoutReadMetadataPermission,
      readFileMetadata,
      readFileMetadataWithoutReadMetadataPermission,
      readFileContent,
      readFileContentWithoutReadContentPermission,
      readFileContentWithoutReadMetadataPermission,
      modifyFile,
      modifyFileWithoutReadMetadataPermission,
      modifyFileWithoutModifyFilePermission,
      deleteFile,
      deleteFileWithoutReadMetadataPermission,
      deleteFileWithoutModifyFilePermission
    )

  private def authKernel(
    collectionsRepoState: Ref[CollectionsRepoFake.CollectionsRepoState]
  ): ZLayer[TestEnvironment, Nothing, AuthorizationKernel] =
    Database.none ++ CollectionsRepoFake.create(collectionsRepoState) >>> AuthorizationKernel.live

  val ExampleSubject: Subject = Subject(ExampleUserId)

  val GetCollectionDetailsOperation: GetCollectionDetails = GetCollectionDetails(ExampleSubject, ExampleCollectionId)

  val SetEncryptionKeyOperation: SetEncryptionKey = SetEncryptionKey(ExampleSubject, ExampleCollectionId, ExampleFuuid1)

  val GetEncryptionKeyOperation: GetEncryptionKey = GetEncryptionKey(ExampleSubject, ExampleCollectionId)

  val CreateFileOperation: CreateFile = CreateFile(ExampleSubject, CollectionId(ExampleCollectionId))

  val ReadFileMetadataOperation: ReadMetadata = ReadMetadata(ExampleSubject, CollectionId(ExampleCollectionId))

  val ReadContentOperation: ReadContent = ReadContent(ExampleSubject, CollectionId(ExampleCollectionId))

  val ModifyFileOperation: ModifyFile = ModifyFile(ExampleSubject, CollectionId(ExampleCollectionId))

  val DeleteFileOperation: DeleteFile = DeleteFile(ExampleSubject, CollectionId(ExampleCollectionId))

  private val getCollectionDetails = testM("should allow to get collection details") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleUserId, accepted = true))
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(GetCollectionDetailsOperation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isRight(equalTo(())))
  }

  private val getCollectionDetailsIfCollectionNotAssignedToUser =
    testM("should not allow to get collection details if collection is not assigned to user") {
      val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true))
      )

      for {
        collectionsRepo <- Ref.make(collectionsRepoState)
        result          <- AuthorizationKernel.authorizeOperation(GetCollectionDetailsOperation).provideLayer(authKernel(collectionsRepo)).either
      } yield assert(result)(isLeft(equalTo(OperationNotPermitted(GetCollectionDetailsOperation))))
    }

  private val setEncryptionKeyByKeyOwner = testM("should allow to set encryption key to himself") {
    val operation = SetEncryptionKeyOperation.copy(userId = ExampleUserId)

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleUserId, accepted = true))
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(operation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isRight(equalTo(())))
  }

  private val setEncryptionKeyByCollectionAdmin = testM("should allow to set encryption key by collection admin") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection))
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(SetEncryptionKeyOperation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isRight(equalTo(())))
  }

  private val setEncryptionKeyIfCollectionNotAssigned =
    testM("should not allow to set encryption key is collection is not assigned to user") {
      val operation = SetEncryptionKeyOperation.copy(userId = ExampleUserId)

      val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState()

      for {
        collectionsRepo <- Ref.make(collectionsRepoState)
        result          <- AuthorizationKernel.authorizeOperation(operation).provideLayer(authKernel(collectionsRepo)).either
      } yield assert(result)(isLeft(equalTo(OperationNotPermitted(operation))))
    }

  private val getEncryptionKey = testM("should allow to get encryption key of collection") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleUserId, accepted = true))
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(GetEncryptionKeyOperation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isRight(equalTo(())))
  }

  private val getEncryptionKeyIfCollectionNotAssignedToUser =
    testM("should not allow to get encryption key of collection if collection not assigned to user") {
      val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true))
      )

      for {
        collectionsRepo <- Ref.make(collectionsRepoState)
        result          <- AuthorizationKernel.authorizeOperation(GetEncryptionKeyOperation).provideLayer(authKernel(collectionsRepo)).either
      } yield assert(result)(isLeft(equalTo(OperationNotPermitted(GetEncryptionKeyOperation))))
    }

  private val createFile = testM("should allow to create file") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile),
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      )
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(CreateFileOperation).provideLayer(authKernel(collectionsRepo))
    } yield assert(result)(isUnit)
  }

  private val createFileWithoutCreatePermission = testM("should not allow to create file if user has no CreateFile permission") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      )
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(CreateFileOperation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isLeft(equalTo(OperationNotPermitted(CreateFileOperation))))
  }

  private val createFileWithoutReadMetadataPermission = testM("should not allow to create file if user has no ReadMetadata permission") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile)
      )
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(CreateFileOperation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isLeft(equalTo(OperationNotPermitted(CreateFileOperation))))
  }

  private val readFileMetadata = testM("should allow to read file metadata") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      )
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(ReadFileMetadataOperation).provideLayer(authKernel(collectionsRepo))
    } yield assert(result)(isUnit)
  }

  private val readFileMetadataWithoutReadMetadataPermission =
    testM("should not allow to read file metadata if user has no read metadata permission") {
      val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
        permissions = Set()
      )

      for {
        collectionsRepo <- Ref.make(collectionsRepoState)
        result          <- AuthorizationKernel.authorizeOperation(ReadFileMetadataOperation).provideLayer(authKernel(collectionsRepo)).either
      } yield assert(result)(isLeft(equalTo(OperationNotPermitted(ReadFileMetadataOperation))))
    }

  private val readFileContent = testM("should allow to read file content") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFile)
      )
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(ReadContentOperation).provideLayer(authKernel(collectionsRepo))
    } yield assert(result)(isUnit)
  }

  private val readFileContentWithoutReadContentPermission =
    testM("should not allow to read file content if user has no ReadFile permission") {
      val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
        permissions = Set(
          PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
        )
      )

      for {
        collectionsRepo <- Ref.make(collectionsRepoState)
        result          <- AuthorizationKernel.authorizeOperation(ReadContentOperation).provideLayer(authKernel(collectionsRepo)).either
      } yield assert(result)(isLeft(equalTo(OperationNotPermitted(ReadContentOperation))))
    }

  private val readFileContentWithoutReadMetadataPermission =
    testM("should not allow to read file content if user has no ReadFileMetadata permission") {
      val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
        permissions = Set(
          PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFile)
        )
      )

      for {
        collectionsRepo <- Ref.make(collectionsRepoState)
        result          <- AuthorizationKernel.authorizeOperation(ReadContentOperation).provideLayer(authKernel(collectionsRepo)).either
      } yield assert(result)(isLeft(equalTo(OperationNotPermitted(ReadContentOperation))))
    }

  private val modifyFile = testM("should allow to modify file") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile)
      )
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(ModifyFileOperation).provideLayer(authKernel(collectionsRepo))
    } yield assert(result)(isUnit)
  }

  private val modifyFileWithoutReadMetadataPermission = testM("should not allow to modify file if user has no ReadMetadataPermission") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile)
      )
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(ModifyFileOperation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isLeft(equalTo(OperationNotPermitted(ModifyFileOperation))))
  }

  private val modifyFileWithoutModifyFilePermission = testM("should not allow to modify file if user has no ModifyFile permission") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      )
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(ModifyFileOperation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isLeft(equalTo(OperationNotPermitted(ModifyFileOperation))))
  }

  private val deleteFile = testM("should allow to delete file") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile)
      )
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(DeleteFileOperation).provideLayer(authKernel(collectionsRepo))
    } yield assert(result)(isUnit)
  }

  private val deleteFileWithoutReadMetadataPermission = testM("should not allow to delete file if user has no ReadMetadata permission") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile)
      )
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(DeleteFileOperation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isLeft(equalTo(OperationNotPermitted(DeleteFileOperation))))
  }

  private val deleteFileWithoutModifyFilePermission = testM("should not allow to delete file if user has no ModifyFile permission") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(
        PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      )
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(DeleteFileOperation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isLeft(equalTo(OperationNotPermitted(DeleteFileOperation))))
  }

}
