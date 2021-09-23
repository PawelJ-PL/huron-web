package com.github.huronapp.api.domain.files

import cats.syntax.either._
import cats.syntax.eq._
import cats.syntax.show._
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.auth.authorization.{
  AuthorizationKernel,
  CreateFile,
  DeleteFile,
  ModifyFile,
  OperationNotPermitted,
  ReadContent,
  ReadMetadata
}
import com.github.huronapp.api.constants.{Collections, Files, MiscConstants, Users}
import com.github.huronapp.api.domain.collections.{Collection, CollectionId, CollectionPermission}
import com.github.huronapp.api.domain.files.FilesService.FilesService
import com.github.huronapp.api.domain.files.dto.{EncryptedContent, NewDirectory, NewFile, NewVersionReq, UpdateStorageUnitMetadataReq}
import com.github.huronapp.api.domain.files.dto.fields.{ContentDigest, Description, EncryptedBytes, EncryptedContentAlgorithm, FileName, Iv}
import com.github.huronapp.api.domain.users.UserId
import com.github.huronapp.api.testdoubles.{
  CollectionsRepoFake,
  CryptoStub,
  FileSystemServiceFake,
  FilesMetadataRepositoryFake,
  NotStarted,
  OutboxServiceFake,
  RandomUtilsStub,
  StoredTask
}
import com.github.huronapp.api.utils.OptionalValue
import com.github.huronapp.api.utils.outbox.OutboxCommand.DeleteFiles
import com.github.huronapp.api.utils.outbox.Task
import io.github.gaelrenoux.tranzactio.doobie.Database
import zio.logging.slf4j.Slf4jLogger
import zio.{Ref, UIO, ZLayer}
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert}
import zio.test.Assertion.{equalTo, hasSameElements, isEmpty, isLeft}

object FilesServiceSpec extends DefaultRunnableSpec with Users with Collections with Files with MiscConstants {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Files service spec")(
      createDirectoryTest,
      createDirectoryWhenForbiddenTest,
      createDirectoryIfExistsTest,
      createDirectoryIfParentIsFileTest,
      createFileTest,
      createFileNotAuthorizedTest,
      createFileIfExistsTest,
      createFileParentNotFoundTest,
      createFileParentNotDirectoryTest,
      createFileEncryptionKeyVersionDoesNotMatchTest,
      getLatestFileVersionTest,
      getSpecificFileVersionTest,
      getFileUnauthorizedTest,
      getFileNotFoundTest,
      getFileContentLatestVersionTest,
      getFileContentSpecificVersionTest,
      getFileContentUnauthorizedTest,
      getFileContentFileNotFoundTest,
      getFileContentObjectNotAFileTest,
      getFileContentDigestMismatchTest,
      getParentsTest,
      getParentsWithLimitTest,
      getParentsWithGreaterLimitTest,
      getParentsForbiddenTest,
      getParentsIfFileNotFoundTest,
      listChildrenTest,
      listChildrenForbiddenTest,
      listChildrenNotFoundTest,
      listChildrenParentNotDirectoryTest,
      updateMetadataTest,
      updateMetadataNoUpdatesTest,
      updateMetadataDirectoryWithDescriptionTest,
      updateMetadataParentSetToSelfTest,
      updateMetadataForbiddenTest,
      updateMetadataFileNotFoundTest,
      updateMetadataParentNotFoundTest,
      updateMetadataParentNotDirectoryTest,
      updateMetadataFileExistsTest,
      updateMetadataCircularParentTest,
      addFileVersionTest,
      addFileVersionForbiddenTest,
      addFileVersionFileNotFoundTest,
      addFileVersionContentNotChangedTest,
      addFileVersionKeyVersionMismatchTest,
      listVersionsTest,
      listVersionsForbiddenTest,
      listVersionsFileNotFoundTest,
      listVersionsNotAFileTest,
      deleteDirectoryTest,
      deleteFileTest,
      deleteEmptyDirectoryTest,
      deleteDirectoryForbiddenTest,
      deleteDirectoryNotFoundTest,
      deleteDirectoryWithChildrenTest,
      deleteVersionTest,
      deleteVersionForbiddenTest,
      deleteVersionFileNotFoundTest,
      deleteVersionVersionNotFoundTest,
      deleteVersionNotAFileTest,
      deleteLatestVersionTest
    )

  private def createService(
    filesRepoState: Ref[List[StorageUnit]],
    collectionsRepoState: Ref[CollectionsRepoFake.CollectionsRepoState],
    fsState: Ref[Map[String, Array[Byte]]],
    outboxState: Ref[List[StoredTask]]
  ): ZLayer[TestEnvironment, Nothing, FilesService] = {
    val collectionsRepo = CollectionsRepoFake.create(collectionsRepoState)
    val db = Database.none
    val logger = Slf4jLogger.make((_, str) => str)
    db ++
      FilesMetadataRepositoryFake.create(filesRepoState) ++
      (db ++ collectionsRepo >>> AuthorizationKernel.live) ++
      RandomUtilsStub.create ++
      logger ++
      CryptoStub.create ++
      FileSystemServiceFake.create(fsState) ++
      collectionsRepo ++
      OutboxServiceFake.create(outboxState) >>> FilesService.live
  }

  private def emptyCollectionsRepoWithPermissions(
    permissions: Set[CollectionPermission] = Set(CollectionPermission.ManageCollection, CollectionPermission.ReadFile,
      CollectionPermission.ReadFileMetadata, CollectionPermission.CreateFile, CollectionPermission.ModifyFile)
  ): UIO[Ref[CollectionsRepoFake.CollectionsRepoState]] = {
    val permissionEntries = permissions.map(p => CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, p))
    val state = CollectionsRepoFake.CollectionsRepoState(permissions = permissionEntries)
    Ref.make(state)
  }

  val userId: UserId = UserId(ExampleUserId)

  val collectionId: CollectionId = CollectionId(ExampleCollectionId)

  private val newDirDto: NewDirectory = NewDirectory(None, FileName(ExampleDirectoryName))

  private val newFileDto: NewFile = NewFile(
    None,
    FileName(ExampleFileName),
    Some(Description(ExampleFileDescription)),
    None,
    EncryptedContent(
      EncryptedContentAlgorithm(ExampleFileContentAlgorithm),
      Iv(ExampleFileContentIv),
      ExampleEncryptionKeyVersion,
      EncryptedBytes(ExampleFileContent)
    ),
    ContentDigest(ExampleFilePlainTextDigest)
  )

  private val updateMetadataDto = UpdateStorageUnitMetadataReq(None, Some(FileName("new-file-name.txt")), None)

  private val newFileVersionDto = NewVersionReq(
    None,
    EncryptedContent(
      EncryptedContentAlgorithm(ExampleFileContentAlgorithm),
      Iv("0a1b2c"),
      ExampleEncryptionKeyVersion,
      EncryptedBytes(Array(100.toByte, 95.toByte, 90.toByte))
    ),
    ContentDigest("newDigest")
  )

  private val createDirectoryTest = testM("Should create new directory") {

    val expectedDir = ExampleDirectoryMetadata.copy(id = FileId(FirstRandomFuuid), createdAt = Now)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(List.empty[StorageUnit])
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .createDirectoryAs(userId, collectionId, newDirDto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(equalTo(expectedDir)) &&
      assert(finalFilesRepoState)(hasSameElements(List(expectedDir))) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val createDirectoryWhenForbiddenTest = testM("Should not create new directory if user has not permissions") {

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(List.empty[StorageUnit])
      collectionsRepo     <- emptyCollectionsRepoWithPermissions(Set.empty)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .createDirectoryAs(userId, collectionId, newDirDto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(
      isLeft(equalTo(AuthorizationError(OperationNotPermitted(CreateFile(Subject(ExampleUserId), CollectionId(ExampleCollectionId))))))
    ) &&
      assert(finalFilesRepoState)(hasSameElements(List.empty)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val createDirectoryIfExistsTest = testM("Should not create new directory if file with the same name exists") {

    val initialFilesRepoState: List[StorageUnit] =
      List(ExampleFileMetadata.copy(parentId = Some(ExampleDirectoryId), name = "conflict_name"))

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <-
        FilesService
          .createDirectoryAs(userId, collectionId, newDirDto.copy(name = FileName("conflict_name"), parent = Some(ExampleDirectoryId.id)))
          .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
          .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(
      isLeft(equalTo(FileAlreadyExists(CollectionId(ExampleCollectionId), Some(ExampleDirectoryId), "conflict_name")))
    ) &&
      assert(finalFilesRepoState)(hasSameElements(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val createDirectoryIfParentIsFileTest = testM("Should not create new directory if parent is not directory") {

    val initFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <-
        FilesService
          .createDirectoryAs(userId, collectionId, newDirDto.copy(name = FileName("new directory"), parent = Some(ExampleFileId.id)))
          .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
          .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(ParentIsNotDirectory(CollectionId(ExampleCollectionId), ExampleFileId)))) &&
      assert(finalFilesRepoState)(hasSameElements(initFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val createFileTest = testM("Should create new file") {
    val expectedFile =
      ExampleFileMetadata.copy(
        id = FileId(FirstRandomFuuid),
        versionId = FileVersionId(SecondRandomFuuid),
        createdAt = Now,
        updatedAt = Now
      )

    val expectedFsPath = show"user_files/$ExampleCollectionId/${FirstRandomFuuid.show.substring(0, 2)}/$FirstRandomFuuid/$SecondRandomFuuid"

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo   <- Ref.make(List.empty[StorageUnit])
      collectionsRepo     <- Ref.make(collectionsRepoState)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .createFileAs(userId, collectionId, newFileDto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(equalTo(expectedFile)) &&
      assert(finalFilesRepoState)(hasSameElements(List(expectedFile))) &&
      assert(finalFsState.map { case (p, c) => (p, c.toList) })(equalTo(Map(expectedFsPath -> ExampleFileContent.toList))) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val createFileNotAuthorizedTest = testM("Should not create new file if user has not enough permissions") {

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo   <- Ref.make(List.empty[StorageUnit])
      collectionsRepo     <- Ref.make(collectionsRepoState)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .createFileAs(userId, collectionId, newFileDto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(
      isLeft(equalTo(AuthorizationError(OperationNotPermitted(CreateFile(Subject(ExampleUserId), CollectionId(ExampleCollectionId))))))
    ) &&
      assert(finalFilesRepoState)(isEmpty) &&
      assert(finalFsState)(isEmpty) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val createFileIfExistsTest = testM("Should not create new file if file with the same name exists") {
    val initialFilesRepoState: List[StorageUnit] =
      List(ExampleDirectoryMetadata, ExampleFileMetadata.copy(parentId = Some(ExampleDirectoryId)))

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- Ref.make(collectionsRepoState)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <-
        FilesService
          .createFileAs(userId, collectionId, newFileDto.copy(parent = Some(ExampleDirectoryId.id), name = FileName(ExampleFileName)))
          .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
          .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(
      isLeft(equalTo(FileAlreadyExists(CollectionId(ExampleCollectionId), Some(ExampleDirectoryId), ExampleFileName)))
    ) &&
      assert(finalFilesRepoState)(hasSameElements(initialFilesRepoState)) &&
      assert(finalFsState)(isEmpty) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val createFileParentNotFoundTest = testM("Should not create new file if parent not found") {

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo   <- Ref.make(List.empty[StorageUnit])
      collectionsRepo     <- Ref.make(collectionsRepoState)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .createFileAs(userId, collectionId, newFileDto.copy(parent = Some(ExampleDirectoryId.id)))
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(ParentNotFound(CollectionId(ExampleCollectionId), ExampleDirectoryId)))) &&
      assert(finalFilesRepoState)(isEmpty) &&
      assert(finalFsState)(isEmpty) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val createFileParentNotDirectoryTest = testM("Should not create new file if parent is not a directory") {
    val initialFileRepoState: List[StorageUnit] = List(ExampleFileMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo   <- Ref.make(initialFileRepoState)
      collectionsRepo     <- Ref.make(collectionsRepoState)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .createFileAs(userId, collectionId, newFileDto.copy(parent = Some(ExampleFileId.id)))
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(ParentIsNotDirectory(CollectionId(ExampleCollectionId), ExampleFileId)))) &&
      assert(finalFilesRepoState)(equalTo(initialFileRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val createFileEncryptionKeyVersionDoesNotMatchTest =
    testM("Should not create new file if encryption key version does not match") {
      val content = newFileDto.content.copy(encryptionKeyVersion = ExampleFuuid1)
      val dto = newFileDto.copy(content = content)

      val initialFsState = Map.empty[String, Array[Byte]]

      val initialOutboxState = List.empty[StoredTask]

      val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        permissions = Set(
          CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile),
          CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
        ),
        collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
      )

      for {
        filesMetadataRepo   <- Ref.make(List.empty[StorageUnit])
        collectionsRepo     <- Ref.make(collectionsRepoState)
        fs                  <- Ref.make(initialFsState)
        outbox              <- Ref.make(initialOutboxState)
        result              <- FilesService
                                 .createFileAs(userId, collectionId, dto)
                                 .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                                 .either
        finalFilesRepoState <- filesMetadataRepo.get
        finalFsState        <- fs.get
        finalOutboxState    <- outbox.get
      } yield assert(result)(
        isLeft(equalTo(EncryptionKeyVersionMismatch(CollectionId(ExampleCollectionId), ExampleFuuid1, ExampleEncryptionKeyVersion)))
      ) &&
        assert(finalFilesRepoState)(isEmpty) &&
        assert(finalFsState)(equalTo(initialFsState)) &&
        assert(finalOutboxState)(equalTo(initialOutboxState))
    }

  private val getLatestFileVersionTest = testM("Should get latest file metadata if no version provided") {
    val file1v1 = File(
      FileId(ExampleFuuid1),
      CollectionId(ExampleCollectionId),
      None,
      "f1.txt",
      Some(ExampleFileDescription),
      FileVersionId(ExampleFuuid2),
      None,
      10L,
      None,
      "dig1",
      "dig2",
      EncryptionParams("AES-CBC", "aa", ExampleEncryptionKeyVersion),
      Now,
      Now
    )

    val file1v2 = file1v1.copy(versionId = FileVersionId(ExampleFuuid3), updatedAt = Now.plusSeconds(3600))

    val filesRepoState: List[StorageUnit] = List(file1v1, ExampleFileMetadata, file1v2)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getStorageUnitMetadataAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), FileId(ExampleFuuid1), None)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
    } yield assert(result)(equalTo(file1v2))
  }

  private val getSpecificFileVersionTest = testM("Should get file metadata with specific version") {
    val file1v1 = File(
      FileId(ExampleFuuid1),
      CollectionId(ExampleCollectionId),
      None,
      "f1.txt",
      Some(ExampleFileDescription),
      FileVersionId(ExampleFuuid2),
      None,
      10L,
      None,
      "dig1",
      "dig2",
      EncryptionParams("AES-CBC", "aa", ExampleEncryptionKeyVersion),
      Now,
      Now
    )

    val file1v2 = file1v1.copy(versionId = FileVersionId(ExampleFuuid3), updatedAt = Now.plusSeconds(3600))

    val filesRepoState: List[StorageUnit] = List(file1v1, ExampleFileMetadata, file1v2)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getStorageUnitMetadataAs(
                               UserId(ExampleUserId),
                               CollectionId(ExampleCollectionId),
                               FileId(ExampleFuuid1),
                               Some(file1v1.versionId)
                             )
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
    } yield assert(result)(equalTo(file1v1))
  }

  private val getFileUnauthorizedTest = testM("Should not get file metadata if use has no permissions") {
    val filesRepoState: List[StorageUnit] = List(ExampleFileMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getStorageUnitMetadataAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), ExampleFileId, None)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(
      isLeft(equalTo(AuthorizationError(OperationNotPermitted(ReadMetadata(Subject(ExampleUserId), CollectionId(ExampleCollectionId))))))
    )
  }

  private val getFileNotFoundTest = testM("Should not get file if file not found") {

    val filesRepoState: List[StorageUnit] = List()

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.CreateFile),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getStorageUnitMetadataAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), ExampleFileId, None)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(isLeft(equalTo(FileNotFound(CollectionId(ExampleCollectionId), ExampleFileId, None))))
  }

  private val getFileContentLatestVersionTest = testM("Should get file content latest version if version not provided") {
    val f1v1 = ExampleFileMetadata

    val f1v2 = ExampleFileMetadata.copy(
      versionId = FileVersionId(ExampleFuuid1),
      encryptedDigest = "digest(010203)",
      updatedAt = f1v1.updatedAt.plusSeconds(3600)
    )

    val filesRepoState: List[StorageUnit] = List(f1v1, f1v2)

    val initialFsState = Map(
      (
        show"user_files/$ExampleCollectionId/${ExampleFileId.id.show.substring(0, 2)}/$ExampleFileId/$ExampleFileVersionId",
        ExampleFileContent
      ),
      (
        show"user_files/$ExampleCollectionId/${ExampleFileId.id.show.substring(0, 2)}/$ExampleFileId/$ExampleFuuid1",
        Array(1.toByte, 2.toByte, 3.toByte)
      )
    )

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFile)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getFileContentAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), ExampleFileId, None)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
    } yield assert(result._1.toList)(equalTo(List(1.toByte, 2.toByte, 3.toByte))) &&
      assert(result._2)(equalTo(f1v2))
  }

  private val getFileContentSpecificVersionTest = testM("Should get file content specific version if version was provided") {
    val f1v1 = ExampleFileMetadata

    val f1v2 = ExampleFileMetadata.copy(
      versionId = FileVersionId(ExampleFuuid1),
      encryptedDigest = "digest(010203)",
      updatedAt = f1v1.updatedAt.plusSeconds(3600)
    )

    val filesRepoState: List[StorageUnit] = List(f1v1, f1v2)

    val initialFsState = Map(
      (
        show"user_files/$ExampleCollectionId/${ExampleFileId.id.show.substring(0, 2)}/$ExampleFileId/$ExampleFileVersionId",
        ExampleFileContent
      ),
      (
        show"user_files/$ExampleCollectionId/${ExampleFileId.id.show.substring(0, 2)}/$ExampleFileId/$ExampleFuuid1",
        Array(1.toByte, 2.toByte, 3.toByte)
      )
    )

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFile)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getFileContentAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), ExampleFileId, Some(ExampleFileVersionId))
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
    } yield assert(result._1.toList)(equalTo(ExampleFileContent.toList)) &&
      assert(result._2)(equalTo(f1v1))
  }

  private val getFileContentUnauthorizedTest = testM("Should not get file content if user has no sufficient permissions") {
    val f1v1 = ExampleFileMetadata

    val f1v2 = ExampleFileMetadata.copy(
      versionId = FileVersionId(ExampleFuuid1),
      encryptedDigest = "digest(010203)",
      updatedAt = f1v1.updatedAt.plusSeconds(3600)
    )

    val filesRepoState: List[StorageUnit] = List(f1v1, f1v2)

    val initialFsState = Map(
      (
        show"user_files/$ExampleCollectionId/${ExampleFileId.id.show.substring(0, 2)}/$ExampleFileId/$ExampleFileVersionId",
        ExampleFileContent
      ),
      (
        show"user_files/$ExampleCollectionId/${ExampleFileId.id.show.substring(0, 2)}/$ExampleFileId/$ExampleFuuid1",
        Array(1.toByte, 2.toByte, 3.toByte)
      )
    )

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getFileContentAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), ExampleFileId, None)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(
      isLeft(equalTo(AuthorizationError(OperationNotPermitted(ReadContent(Subject(ExampleUserId), CollectionId(ExampleCollectionId))))))
    )
  }

  private val getFileContentFileNotFoundTest = testM("Should not get file content if file not found") {

    val filesRepoState: List[StorageUnit] = List.empty

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFile)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getFileContentAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), ExampleFileId, Some(ExampleFileVersionId))
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(
      isLeft(equalTo(FileNotFound(CollectionId(ExampleCollectionId), ExampleFileId, Some(ExampleFileVersionId))))
    )
  }

  private val getFileContentObjectNotAFileTest = testM("Should not get file content if object is not a file") {

    val filesRepoState: List[StorageUnit] = List(ExampleDirectoryMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFile)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getFileContentAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), ExampleDirectoryId, None)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(isLeft(equalTo(NotAFile(CollectionId(ExampleCollectionId), ExampleDirectoryId))))
  }

  private val getFileContentDigestMismatchTest = testM("Should not get file content if content digest mismatch") {
    val f1v1 = ExampleFileMetadata

    val f1v2 = ExampleFileMetadata.copy(
      versionId = FileVersionId(ExampleFuuid1),
      encryptedDigest = "digest(999999)",
      updatedAt = f1v1.updatedAt.plusSeconds(3600)
    )

    val filesRepoState: List[StorageUnit] = List(f1v1, f1v2)

    val initialFsState = Map(
      (
        show"user_files/$ExampleCollectionId/${ExampleFileId.id.show.substring(0, 2)}/$ExampleFileId/$ExampleFileVersionId",
        ExampleFileContent
      ),
      (
        show"user_files/$ExampleCollectionId/${ExampleFileId.id.show.substring(0, 2)}/$ExampleFileId/$ExampleFuuid1",
        Array(1.toByte, 2.toByte, 3.toByte)
      )
    )

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFile)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getFileContentAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), ExampleFileId, None)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .orElseFail(new Throwable("An error"))
                             .resurrect
                             .either
    } yield assert(result.leftMap(_.getMessage))(
      isLeft(
        equalTo(
          show"File user_files/$ExampleCollectionId/${ExampleFileId.id.show.substring(0, 2)}/$ExampleFileId/$ExampleFuuid1 digest mismatch. Expected digest(999999), found digest(010203)"
        )
      )
    )
  }

  private val getParentsTest = testM("Should get parents tree") {
    val d1 = Directory(ExampleDirectoryId, CollectionId(ExampleCollectionId), None, "d1", Now)
    val d2 = Directory(FileId(ExampleFuuid1), CollectionId(ExampleCollectionId), None, "d2", Now)
    val d2_f1 = File(
      FileId(ExampleFuuid2),
      CollectionId(ExampleCollectionId),
      Some(FileId(ExampleFuuid1)),
      "d2_f1.txt",
      Some(ExampleFileDescription),
      FileVersionId(ExampleFuuid3),
      None,
      0L,
      None,
      "digest",
      "digest",
      EncryptionParams("AES-CBC", "aaaa", ExampleEncryptionKeyVersion),
      Now,
      Now
    )
    val d1_1 = d1.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id), name = "d1_1")
    val d1_2 = d1.copy(id = FileId(ExampleFuuid5), parentId = Some(d1.id), name = "d1_2")
    val d1_f1 = d2_f1.copy(id = FileId(ExampleFuuid6), parentId = Some(d1.id), name = "d1_f1.txt")
    val d1_1_1 = d1.copy(id = FileId(ExampleFuuid7), parentId = Some(d1_1.id), name = "d1_1_1")
    val d1_1_2 = d1.copy(id = FileId(ExampleFuuid8), parentId = Some(d1_1.id), name = "d1_1_2")
    val d1_1_3 = d1.copy(id = FileId(ExampleFuuid9), parentId = Some(d1_1.id), name = "d1_1_3")
    val d1_1_3_1 = d1.copy(id = FileId(ExampleFuuid10), parentId = Some(d1_1_3.id), name = "d1_1_3_1")
    val d1_1_2_1 = d1.copy(id = FileId(ExampleFuuid11), parentId = Some(d1_1_2.id), name = "d1_1_2_1")
    val d1_1_2_1_1 = d1.copy(id = FileId(ExampleFuuid12), parentId = Some(d1_1_2_1.id), name = "d1_1_2_1_1")
    val d1_1_2_1_2 = d1.copy(id = FileId(ExampleFuuid13), parentId = Some(d1_1_2_1.id), name = "d1_1_2_1_2")
    val d1_1_2_1_2_f1 = d2_f1.copy(id = FileId(ExampleFuuid14), parentId = Some(d1_1_2_1_2.id), name = "d1_1_2_1_2_f1.txt")

    val filesRepoState: List[StorageUnit] =
      List(d1, d2, d2_f1, d1_1, d1_2, d1_f1, d1_1_1, d1_1_2, d1_1_3, d1_1_3_1, d1_1_2_1, d1_1_2_1_1, d1_1_2_1_2, d1_1_2_1_2_f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getParentsAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), d1_1_2_1_2_f1.id, None)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
    } yield assert(result)(equalTo(List(d1_1_2_1_2, d1_1_2_1, d1_1_2, d1_1, d1)))
  }

  private val getParentsWithLimitTest = testM("Should get limited parents tree") {
    val d1 = Directory(ExampleDirectoryId, CollectionId(ExampleCollectionId), None, "d1", Now)
    val d2 = Directory(FileId(ExampleFuuid1), CollectionId(ExampleCollectionId), None, "d2", Now)
    val d2_f1 = File(
      FileId(ExampleFuuid2),
      CollectionId(ExampleCollectionId),
      Some(FileId(ExampleFuuid1)),
      "d2_f1.txt",
      Some(ExampleFileDescription),
      FileVersionId(ExampleFuuid3),
      None,
      0L,
      None,
      "digest",
      "digest",
      EncryptionParams("AES-CBC", "aaaa", ExampleEncryptionKeyVersion),
      Now,
      Now
    )
    val d1_1 = d1.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id), name = "d1_1")
    val d1_2 = d1.copy(id = FileId(ExampleFuuid5), parentId = Some(d1.id), name = "d1_2")
    val d1_f1 = d2_f1.copy(id = FileId(ExampleFuuid6), parentId = Some(d1.id), name = "d1_f1.txt")
    val d1_1_1 = d1.copy(id = FileId(ExampleFuuid7), parentId = Some(d1_1.id), name = "d1_1_1")
    val d1_1_2 = d1.copy(id = FileId(ExampleFuuid8), parentId = Some(d1_1.id), name = "d1_1_2")
    val d1_1_3 = d1.copy(id = FileId(ExampleFuuid9), parentId = Some(d1_1.id), name = "d1_1_3")
    val d1_1_3_1 = d1.copy(id = FileId(ExampleFuuid10), parentId = Some(d1_1_3.id), name = "d1_1_3_1")
    val d1_1_2_1 = d1.copy(id = FileId(ExampleFuuid11), parentId = Some(d1_1_2.id), name = "d1_1_2_1")
    val d1_1_2_1_1 = d1.copy(id = FileId(ExampleFuuid12), parentId = Some(d1_1_2_1.id), name = "d1_1_2_1_1")
    val d1_1_2_1_2 = d1.copy(id = FileId(ExampleFuuid13), parentId = Some(d1_1_2_1.id), name = "d1_1_2_1_2")
    val d1_1_2_1_2_f1 = d2_f1.copy(id = FileId(ExampleFuuid14), parentId = Some(d1_1_2_1_2.id), name = "d1_1_2_1_2_f1.txt")

    val filesRepoState: List[StorageUnit] =
      List(d1, d2, d2_f1, d1_1, d1_2, d1_f1, d1_1_1, d1_1_2, d1_1_3, d1_1_3_1, d1_1_2_1, d1_1_2_1_1, d1_1_2_1_2, d1_1_2_1_2_f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getParentsAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), d1_1_2_1_2_f1.id, Some(2))
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
    } yield assert(result)(equalTo(List(d1_1_2_1_2, d1_1_2_1)))
  }

  private val getParentsWithGreaterLimitTest = testM("Should get all parents tree if limit is grater than parents number") {
    val d1 = Directory(ExampleDirectoryId, CollectionId(ExampleCollectionId), None, "d1", Now)
    val d2 = Directory(FileId(ExampleFuuid1), CollectionId(ExampleCollectionId), None, "d2", Now)
    val d2_f1 = File(
      FileId(ExampleFuuid2),
      CollectionId(ExampleCollectionId),
      Some(FileId(ExampleFuuid1)),
      "d2_f1.txt",
      Some(ExampleFileDescription),
      FileVersionId(ExampleFuuid3),
      None,
      0L,
      None,
      "digest",
      "digest",
      EncryptionParams("AES-CBC", "aaaa", ExampleEncryptionKeyVersion),
      Now,
      Now
    )
    val d1_1 = d1.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id), name = "d1_1")
    val d1_2 = d1.copy(id = FileId(ExampleFuuid5), parentId = Some(d1.id), name = "d1_2")
    val d1_f1 = d2_f1.copy(id = FileId(ExampleFuuid6), parentId = Some(d1.id), name = "d1_f1.txt")
    val d1_1_1 = d1.copy(id = FileId(ExampleFuuid7), parentId = Some(d1_1.id), name = "d1_1_1")
    val d1_1_2 = d1.copy(id = FileId(ExampleFuuid8), parentId = Some(d1_1.id), name = "d1_1_2")
    val d1_1_3 = d1.copy(id = FileId(ExampleFuuid9), parentId = Some(d1_1.id), name = "d1_1_3")
    val d1_1_3_1 = d1.copy(id = FileId(ExampleFuuid10), parentId = Some(d1_1_3.id), name = "d1_1_3_1")
    val d1_1_2_1 = d1.copy(id = FileId(ExampleFuuid11), parentId = Some(d1_1_2.id), name = "d1_1_2_1")
    val d1_1_2_1_1 = d1.copy(id = FileId(ExampleFuuid12), parentId = Some(d1_1_2_1.id), name = "d1_1_2_1_1")
    val d1_1_2_1_2 = d1.copy(id = FileId(ExampleFuuid13), parentId = Some(d1_1_2_1.id), name = "d1_1_2_1_2")
    val d1_1_2_1_2_f1 = d2_f1.copy(id = FileId(ExampleFuuid14), parentId = Some(d1_1_2_1_2.id), name = "d1_1_2_1_2_f1.txt")

    val filesRepoState: List[StorageUnit] =
      List(d1, d2, d2_f1, d1_1, d1_2, d1_f1, d1_1_1, d1_1_2, d1_1_3, d1_1_3_1, d1_1_2_1, d1_1_2_1_1, d1_1_2_1_2, d1_1_2_1_2_f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getParentsAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), d1_1_2_1_2_f1.id, Some(1000))
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
    } yield assert(result)(equalTo(List(d1_1_2_1_2, d1_1_2_1, d1_1_2, d1_1, d1)))
  }

  private val getParentsForbiddenTest = testM("Should not get parents tree if user has no permissions") {
    val d1 = Directory(ExampleDirectoryId, CollectionId(ExampleCollectionId), None, "d1", Now)
    val d2 = Directory(FileId(ExampleFuuid1), CollectionId(ExampleCollectionId), None, "d2", Now)
    val d2_f1 = File(
      FileId(ExampleFuuid2),
      CollectionId(ExampleCollectionId),
      Some(FileId(ExampleFuuid1)),
      "d2_f1.txt",
      Some(ExampleFileDescription),
      FileVersionId(ExampleFuuid3),
      None,
      0L,
      None,
      "digest",
      "digest",
      EncryptionParams("AES-CBC", "aaaa", ExampleEncryptionKeyVersion),
      Now,
      Now
    )
    val d1_1 = d1.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id), name = "d1_1")
    val d1_2 = d1.copy(id = FileId(ExampleFuuid5), parentId = Some(d1.id), name = "d1_2")
    val d1_f1 = d2_f1.copy(id = FileId(ExampleFuuid6), parentId = Some(d1.id), name = "d1_f1.txt")
    val d1_1_1 = d1.copy(id = FileId(ExampleFuuid7), parentId = Some(d1_1.id), name = "d1_1_1")
    val d1_1_2 = d1.copy(id = FileId(ExampleFuuid8), parentId = Some(d1_1.id), name = "d1_1_2")
    val d1_1_3 = d1.copy(id = FileId(ExampleFuuid9), parentId = Some(d1_1.id), name = "d1_1_3")
    val d1_1_3_1 = d1.copy(id = FileId(ExampleFuuid10), parentId = Some(d1_1_3.id), name = "d1_1_3_1")
    val d1_1_2_1 = d1.copy(id = FileId(ExampleFuuid11), parentId = Some(d1_1_2.id), name = "d1_1_2_1")
    val d1_1_2_1_1 = d1.copy(id = FileId(ExampleFuuid12), parentId = Some(d1_1_2_1.id), name = "d1_1_2_1_1")
    val d1_1_2_1_2 = d1.copy(id = FileId(ExampleFuuid13), parentId = Some(d1_1_2_1.id), name = "d1_1_2_1_2")
    val d1_1_2_1_2_f1 = d2_f1.copy(id = FileId(ExampleFuuid14), parentId = Some(d1_1_2_1_2.id), name = "d1_1_2_1_2_f1.txt")

    val filesRepoState: List[StorageUnit] =
      List(d1, d2, d2_f1, d1_1, d1_2, d1_f1, d1_1_1, d1_1_2, d1_1_3, d1_1_3_1, d1_1_2_1, d1_1_2_1_1, d1_1_2_1_2, d1_1_2_1_2_f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getParentsAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), d1_1_2_1_2_f1.id, None)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(
      isLeft(equalTo(AuthorizationError(OperationNotPermitted(ReadMetadata(Subject(ExampleUserId), CollectionId(ExampleCollectionId))))))
    )
  }

  private val getParentsIfFileNotFoundTest = testM("Should not get parents tree if file not found") {
    val d1 = Directory(ExampleDirectoryId, CollectionId(ExampleCollectionId), None, "d1", Now)
    val d2 = Directory(FileId(ExampleFuuid1), CollectionId(ExampleCollectionId), None, "d2", Now)
    val d2_f1 = File(
      FileId(ExampleFuuid2),
      CollectionId(ExampleCollectionId),
      Some(FileId(ExampleFuuid1)),
      "d2_f1.txt",
      Some(ExampleFileDescription),
      FileVersionId(ExampleFuuid3),
      None,
      0L,
      None,
      "digest",
      "digest",
      EncryptionParams("AES-CBC", "aaaa", ExampleEncryptionKeyVersion),
      Now,
      Now
    )
    val d1_1 = d1.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id), name = "d1_1")
    val d1_2 = d1.copy(id = FileId(ExampleFuuid5), parentId = Some(d1.id), name = "d1_2")
    val d1_f1 = d2_f1.copy(id = FileId(ExampleFuuid6), parentId = Some(d1.id), name = "d1_f1.txt")
    val d1_1_1 = d1.copy(id = FileId(ExampleFuuid7), parentId = Some(d1_1.id), name = "d1_1_1")
    val d1_1_2 = d1.copy(id = FileId(ExampleFuuid8), parentId = Some(d1_1.id), name = "d1_1_2")
    val d1_1_3 = d1.copy(id = FileId(ExampleFuuid9), parentId = Some(d1_1.id), name = "d1_1_3")
    val d1_1_3_1 = d1.copy(id = FileId(ExampleFuuid10), parentId = Some(d1_1_3.id), name = "d1_1_3_1")
    val d1_1_2_1 = d1.copy(id = FileId(ExampleFuuid11), parentId = Some(d1_1_2.id), name = "d1_1_2_1")
    val d1_1_2_1_1 = d1.copy(id = FileId(ExampleFuuid12), parentId = Some(d1_1_2_1.id), name = "d1_1_2_1_1")
    val d1_1_2_1_2 = d1.copy(id = FileId(ExampleFuuid13), parentId = Some(d1_1_2_1.id), name = "d1_1_2_1_2")
    val d1_1_2_1_2_f1 = d2_f1.copy(id = FileId(ExampleFuuid14), parentId = Some(d1_1_2_1_2.id), name = "d1_1_2_1_2_f1.txt")

    val filesRepoState: List[StorageUnit] =
      List(d1, d2, d2_f1, d1_1, d1_2, d1_f1, d1_1_1, d1_1_2, d1_1_3, d1_1_3_1, d1_1_2_1, d1_1_2_1_1, d1_1_2_1_2, d1_1_2_1_2_f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- Ref.make(collectionsRepoState)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getParentsAs(UserId(ExampleUserId), CollectionId(ExampleCollectionId), FileId(ExampleFuuid15), None)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(isLeft(equalTo(FileNotFound(CollectionId(ExampleCollectionId), FileId(ExampleFuuid15), None))))
  }

  private val listChildrenTest = testM("Should list children") {
    val f1 = ExampleFileMetadata.copy(parentId = Some(ExampleDirectoryId))
    val f2 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), parentId = None)
    val f3 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid2), parentId = Some(ExampleDirectoryId))
    val d1 = Directory(FileId(ExampleFuuid3), collectionId, None, "d1", Now)
    val f4 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id))
    val d2 = Directory(FileId(ExampleFuuid5), collectionId, Some(ExampleDirectoryId), "d2", Now)
    val f5 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid6), parentId = Some(d2.id))

    val filesRepoState: List[StorageUnit] = List(ExampleDirectoryMetadata, f1, f2, f3, d1, f4, d2, f5)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- emptyCollectionsRepoWithPermissions()
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getChildrenAs(userId, collectionId, Some(ExampleDirectoryId))
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
    } yield assert(result)(hasSameElements(List(f1, f3, d2)))
  }

  private val listChildrenForbiddenTest = testM("Should npt list children if user has no permissions") {
    val f1 = ExampleFileMetadata.copy(parentId = Some(ExampleDirectoryId))
    val f2 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), parentId = None)
    val f3 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid2), parentId = Some(ExampleDirectoryId))
    val d1 = Directory(FileId(ExampleFuuid3), collectionId, None, "d1", Now)
    val f4 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id))
    val d2 = Directory(FileId(ExampleFuuid5), collectionId, Some(ExampleDirectoryId), "d2", Now)
    val f5 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid6), parentId = Some(d2.id))

    val filesRepoState: List[StorageUnit] = List(ExampleDirectoryMetadata, f1, f2, f3, d1, f4, d2, f5)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- emptyCollectionsRepoWithPermissions(permissions = Set.empty)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getChildrenAs(userId, collectionId, Some(ExampleDirectoryId))
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(isLeft(equalTo(AuthorizationError(OperationNotPermitted(ReadMetadata(Subject(ExampleUserId), collectionId))))))
  }

  private val listChildrenNotFoundTest = testM("Should not list children if parent not found") {
    val f1 = ExampleFileMetadata.copy(parentId = Some(ExampleDirectoryId))
    val f2 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), parentId = None)
    val f3 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid2), parentId = Some(ExampleDirectoryId))
    val d1 = Directory(FileId(ExampleFuuid3), collectionId, None, "d1", Now)
    val f4 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id))
    val d2 = Directory(FileId(ExampleFuuid5), collectionId, Some(ExampleDirectoryId), "d2", Now)
    val f5 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid6), parentId = Some(d2.id))

    val filesRepoState: List[StorageUnit] = List(ExampleDirectoryMetadata, f1, f2, f3, d1, f4, d2, f5)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- emptyCollectionsRepoWithPermissions()
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getChildrenAs(userId, collectionId, Some(FileId(ExampleFuuid7)))
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(isLeft(equalTo(ParentNotFound(collectionId, FileId(ExampleFuuid7)))))
  }

  private val listChildrenParentNotDirectoryTest = testM("Should not list children if parent is not a directory") {
    val f1 = ExampleFileMetadata.copy(parentId = Some(ExampleDirectoryId))
    val f2 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), parentId = None)
    val f3 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid2), parentId = Some(ExampleDirectoryId))
    val d1 = Directory(FileId(ExampleFuuid3), collectionId, None, "d1", Now)
    val f4 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id))
    val d2 = Directory(FileId(ExampleFuuid5), collectionId, Some(ExampleDirectoryId), "d2", Now)
    val f5 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid6), parentId = Some(d2.id))

    val filesRepoState: List[StorageUnit] = List(ExampleDirectoryMetadata, f1, f2, f3, d1, f4, d2, f5)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo <- Ref.make(filesRepoState)
      collectionsRepo   <- emptyCollectionsRepoWithPermissions()
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .getChildrenAs(userId, collectionId, Some(f1.id))
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(isLeft(equalTo(ParentIsNotDirectory(collectionId, f1.id))))
  }

  private val updateMetadataTest = testM("Should update metadata of the file") {

    val initialFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata)

    val expectedFile = ExampleFileMetadata.copy(name = "new-file-name.txt")

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .updateMetadataAs(userId, collectionId, ExampleFileId, updateMetadataDto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(equalTo(expectedFile)) &&
      assert(finalFilesRepoState)(hasSameElements(List(expectedFile))) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val updateMetadataNoUpdatesTest = testM("Should not update metadata of the file if no updates were provided") {
    val dto = UpdateStorageUnitMetadataReq(None, None, None)

    val initialFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .updateMetadataAs(userId, collectionId, ExampleFileId, dto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(NoUpdates(collectionId, "file metadata", ExampleFileId.id, dto)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val updateMetadataDirectoryWithDescriptionTest =
    testM("Should not update metadata of the directory if description was provided") {
      val dto = UpdateStorageUnitMetadataReq(None, None, Some(OptionalValue(Some(Description("Foo")))))

      val initialFilesRepoState: List[StorageUnit] = List(ExampleDirectoryMetadata)

      val initialFsState = Map.empty[String, Array[Byte]]

      val initialOutboxState = List.empty[StoredTask]

      for {
        filesMetadataRepo   <- Ref.make(initialFilesRepoState)
        collectionsRepo     <- emptyCollectionsRepoWithPermissions()
        fs                  <- Ref.make(initialFsState)
        outbox              <- Ref.make(initialOutboxState)
        result              <- FilesService
                                 .updateMetadataAs(userId, collectionId, ExampleDirectoryMetadata.id, dto)
                                 .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                                 .either
        finalFilesRepoState <- filesMetadataRepo.get
        finalFsState        <- fs.get
        finalOutboxState    <- outbox.get
      } yield assert(result)(isLeft(equalTo(DescriptionAssignedToNonFileObject(collectionId, ExampleDirectoryMetadata.id)))) &&
        assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
        assert(finalFsState)(equalTo(initialFsState)) &&
        assert(finalOutboxState)(equalTo(initialOutboxState))
    }

  private val updateMetadataParentSetToSelfTest = testM("Should not update metadata of the file if new parent is self") {
    val dto = UpdateStorageUnitMetadataReq(Some(OptionalValue(Some(ExampleFileId.id))), None, None)

    val initialFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .updateMetadataAs(userId, collectionId, ExampleFileId, dto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(NewParentSetToSelf(collectionId, ExampleFileId)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val updateMetadataForbiddenTest = testM("Should not update metadata of the file if user has no permissions") {

    val initialFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions(permissions = Set())
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .updateMetadataAs(userId, collectionId, ExampleFileId, updateMetadataDto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(AuthorizationError(OperationNotPermitted(ModifyFile(Subject(ExampleUserId), collectionId)))))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val updateMetadataFileNotFoundTest = testM("Should not update metadata of the file if file not found") {

    val initialFilesRepoState: List[StorageUnit] = List.empty

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .updateMetadataAs(userId, collectionId, ExampleFileId, updateMetadataDto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(FileNotFound(collectionId, ExampleFileId, None)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val updateMetadataParentNotFoundTest = testM("Should not update metadata of the file if new parent not found") {
    val dto = UpdateStorageUnitMetadataReq(Some(OptionalValue(Some(ExampleDirectoryId.id))), None, None)

    val initialFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .updateMetadataAs(userId, collectionId, ExampleFileId, dto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(ParentNotFound(collectionId, ExampleDirectoryId)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val updateMetadataParentNotDirectoryTest = testM("Should not update metadata of the file if new parent is not a directory") {
    val f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), name = "foo.txt")

    val dto = UpdateStorageUnitMetadataReq(Some(OptionalValue(Some(f1.id.id))), None, None)

    val initialFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata, f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .updateMetadataAs(userId, collectionId, ExampleFileId, dto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(ParentIsNotDirectory(collectionId, f1.id)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val updateMetadataFileExistsTest = testM("Should not update metadata of the file if file with the same name already exists") {
    val f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), name = "foo.txt", parentId = Some(ExampleDirectoryId))

    val dto = UpdateStorageUnitMetadataReq(Some(OptionalValue(f1.parentId.map(_.id))), Some(FileName(f1.name)), None)

    val initialFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata, ExampleDirectoryMetadata, f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .updateMetadataAs(userId, collectionId, ExampleFileId, dto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(FileAlreadyExists(collectionId, Some(ExampleDirectoryId), f1.name)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val updateMetadataCircularParentTest = testM("Should not update metadata of the file if circular parent found") {
    val d1 = ExampleDirectoryMetadata.copy(name = "d1")
    val d2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid1), name = "d2")
    val d1_1 = d1.copy(id = FileId(ExampleFuuid2), name = "d1_1", parentId = Some(d1.id))
    val d1_1_1 = d1.copy(id = FileId(ExampleFuuid3), name = "d1_1_1", parentId = Some(d1_1.id))
    val d1_1_1_1 = d1.copy(id = FileId(ExampleFuuid4), name = "d1_1_1_1", parentId = Some(d1_1_1.id))
    val d1_1_1_1_1 = d1.copy(id = FileId(ExampleFuuid5), name = "d1_1_1_1_1", parentId = Some(d1_1_1_1.id))
    val d1_1_1_1_1_1 = d1.copy(id = FileId(ExampleFuuid6), name = "d1_1_1_1_1_1", parentId = Some(d1_1_1_1_1.id))
    val d1_1_1_1_1_1_1 = d1.copy(id = FileId(ExampleFuuid7), name = "d1_1_1_1_1_1_1", parentId = Some(d1_1_1_1_1_1.id))
    val d1_1_1_1_1_1_1_1 = d1.copy(id = FileId(ExampleFuuid8), name = "d1_1_1_1_1_1_1_1", parentId = Some(d1_1_1_1_1_1_1.id))
    val d1_1_1_1_1_1_1_1_1 = d1.copy(id = FileId(ExampleFuuid9), name = "d1_1_1_1_1_1_1_1_1", parentId = Some(d1_1_1_1_1_1_1_1.id))

    val initialFilesRepoState: List[StorageUnit] =
      List(d1, d2, d1_1, d1_1_1, d1_1_1_1, d1_1_1_1_1, d1_1_1_1_1_1, d1_1_1_1_1_1_1, d1_1_1_1_1_1_1_1, d1_1_1_1_1_1_1_1_1)

    val dto = UpdateStorageUnitMetadataReq(Some(OptionalValue(Some(d1_1_1_1_1_1_1_1.id.id))), None, None)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .updateMetadataAs(userId, collectionId, d1_1_1.id, dto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(CircularParentSet(collectionId, d1_1_1.id, d1_1_1_1_1_1_1_1.id)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val addFileVersionTest = testM("Should create new version of the file") {

    val initialFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    val expectedFile = ExampleFileMetadata.copy(
      versionId = FileVersionId(FirstRandomFuuid),
      originalDigest = newFileVersionDto.contentDigest.value,
      encryptedDigest = "digest(645f5a)",
      encryptionParams = ExampleFileMetadata.encryptionParams.copy(iv = newFileVersionDto.content.iv.value),
      updatedAt = ExampleFileMetadata.updatedAt.plusSeconds(3600)
    )

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- Ref.make(collectionsRepoState)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .addFileVersionAs(UserId(ExampleUserId), collectionId, ExampleFileId, newFileVersionDto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(equalTo(expectedFile)) &&
      assert(finalFilesRepoState)(hasSameElements(List(ExampleFileMetadata, expectedFile))) &&
      assert(finalFsState.map { case (path, content) => (path, content.toList) })(
        equalTo(
          initialFsState.map { case (path, content) => (path, content.toList) } ++ Map(
            show"user_files/$collectionId/${ExampleFileId.id.show.substring(0, 2)}/$ExampleFileId/$FirstRandomFuuid" -> newFileVersionDto
              .content
              .bytes
              .value
              .toList
          )
        )
      ) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val addFileVersionForbiddenTest = testM("Should not create new version of the file if user has no permissions") {

    val initialFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- Ref.make(collectionsRepoState)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .addFileVersionAs(UserId(ExampleUserId), collectionId, ExampleFileId, newFileVersionDto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(AuthorizationError(OperationNotPermitted(ModifyFile(Subject(ExampleUserId), collectionId)))))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val addFileVersionFileNotFoundTest = testM("Should not create new version of the file if file not found") {

    val initialFilesRepoState: List[StorageUnit] = List.empty

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- Ref.make(collectionsRepoState)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .addFileVersionAs(UserId(ExampleUserId), collectionId, ExampleFileId, newFileVersionDto)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(FileNotFound(collectionId, ExampleFileId, None)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val addFileVersionContentNotChangedTest = testM("Should not create new version of the file if content was not changed") {

    val initialFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      permissions = Set(
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
        CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile)
      ),
      collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
    )

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- Ref.make(collectionsRepoState)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .addFileVersionAs(
                                 UserId(ExampleUserId),
                                 collectionId,
                                 ExampleFileId,
                                 newFileVersionDto.copy(content = newFileVersionDto.content.copy(bytes = EncryptedBytes(ExampleFileContent)))
                               )
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(FileContentNotChanged(collectionId, ExampleFileId)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val addFileVersionKeyVersionMismatchTest =
    testM("Should not create new version of the file if encryption key version is different than current one") {

      val initialFilesRepoState: List[StorageUnit] = List(ExampleFileMetadata)

      val initialFsState = Map.empty[String, Array[Byte]]

      val initialOutboxState = List.empty[StoredTask]

      val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        permissions = Set(
          CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ReadFileMetadata),
          CollectionsRepoFake.PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ModifyFile)
        ),
        collections = Set(Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId))
      )

      for {
        filesMetadataRepo   <- Ref.make(initialFilesRepoState)
        collectionsRepo     <- Ref.make(collectionsRepoState)
        fs                  <- Ref.make(initialFsState)
        outbox              <- Ref.make(initialOutboxState)
        result              <- FilesService
                                 .addFileVersionAs(
                                   UserId(ExampleUserId),
                                   collectionId,
                                   ExampleFileId,
                                   newFileVersionDto.copy(content = newFileVersionDto.content.copy(encryptionKeyVersion = ExampleFuuid1))
                                 )
                                 .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                                 .either
        finalFilesRepoState <- filesMetadataRepo.get
        finalFsState        <- fs.get
        finalOutboxState    <- outbox.get
      } yield assert(result)(isLeft(equalTo(EncryptionKeyVersionMismatch(collectionId, ExampleFuuid1, ExampleEncryptionKeyVersion)))) &&
        assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
        assert(finalFsState)(equalTo(initialFsState)) &&
        assert(finalOutboxState)(equalTo(initialOutboxState))
    }

  private val listVersionsTest = testM("Should list all versions of the file") {
    val f1v1 = ExampleFileMetadata.copy(updatedAt = Now)
    val f2v1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), versionId = FileVersionId(ExampleFuuid2), updatedAt = Now)
    val f1v2 = f1v1.copy(versionId = FileVersionId(ExampleFuuid3), updatedAt = Now.plusSeconds(10))
    val f2v2 = f2v1.copy(versionId = FileVersionId(ExampleFuuid4), updatedAt = Now.plusSeconds(10))
    val f1v3 = f1v1.copy(versionId = FileVersionId(ExampleFuuid5), updatedAt = Now.plusSeconds(20))
    val f2v3 = f2v1.copy(versionId = FileVersionId(ExampleFuuid6), updatedAt = Now.plusSeconds(20))

    val initialFilesRepoState: List[StorageUnit] = List(f1v2, f2v2, f1v1, f2v1, f2v3, f1v3)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo <- Ref.make(initialFilesRepoState)
      collectionsRepo   <- emptyCollectionsRepoWithPermissions()
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .listVersionsAs(userId, collectionId, f1v1.id)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
    } yield assert(result)(equalTo(List(f1v3, f1v2, f1v1)))
  }

  private val listVersionsForbiddenTest = testM("Should not list versions if user has no permissions") {
    val f1v1 = ExampleFileMetadata.copy(updatedAt = Now)
    val f2v1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), versionId = FileVersionId(ExampleFuuid2), updatedAt = Now)
    val f1v2 = f1v1.copy(versionId = FileVersionId(ExampleFuuid3), updatedAt = Now.plusSeconds(10))
    val f2v2 = f2v1.copy(versionId = FileVersionId(ExampleFuuid4), updatedAt = Now.plusSeconds(10))
    val f1v3 = f1v1.copy(versionId = FileVersionId(ExampleFuuid5), updatedAt = Now.plusSeconds(20))
    val f2v3 = f2v1.copy(versionId = FileVersionId(ExampleFuuid6), updatedAt = Now.plusSeconds(20))

    val initialFilesRepoState: List[StorageUnit] = List(f1v2, f2v2, f1v1, f2v1, f2v3, f1v3)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo <- Ref.make(initialFilesRepoState)
      collectionsRepo   <- emptyCollectionsRepoWithPermissions(permissions = Set.empty)
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .listVersionsAs(userId, collectionId, f1v1.id)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(isLeft(equalTo(AuthorizationError(OperationNotPermitted(ReadMetadata(Subject(ExampleUserId), collectionId))))))
  }

  private val listVersionsFileNotFoundTest = testM("Should not list versions if file was not found") {
    val f1v1 = ExampleFileMetadata.copy(updatedAt = Now)
    val f2v1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), versionId = FileVersionId(ExampleFuuid2), updatedAt = Now)
    val f1v2 = f1v1.copy(versionId = FileVersionId(ExampleFuuid3), updatedAt = Now.plusSeconds(10))
    val f2v2 = f2v1.copy(versionId = FileVersionId(ExampleFuuid4), updatedAt = Now.plusSeconds(10))
    val f1v3 = f1v1.copy(versionId = FileVersionId(ExampleFuuid5), updatedAt = Now.plusSeconds(20))
    val f2v3 = f2v1.copy(versionId = FileVersionId(ExampleFuuid6), updatedAt = Now.plusSeconds(20))

    val initialFilesRepoState: List[StorageUnit] = List(f1v2, f2v2, f1v1, f2v1, f2v3, f1v3)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo <- Ref.make(initialFilesRepoState)
      collectionsRepo   <- emptyCollectionsRepoWithPermissions()
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .listVersionsAs(userId, collectionId, FileId(ExampleFuuid7))
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(isLeft(equalTo(FileNotFound(collectionId, FileId(ExampleFuuid7), None))))
  }

  private val listVersionsNotAFileTest = testM("Should not list versions if object is not a file") {

    val initialFilesRepoState: List[StorageUnit] = List(ExampleDirectoryMetadata)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo <- Ref.make(initialFilesRepoState)
      collectionsRepo   <- emptyCollectionsRepoWithPermissions()
      fs                <- Ref.make(initialFsState)
      outbox            <- Ref.make(initialOutboxState)
      result            <- FilesService
                             .listVersionsAs(userId, collectionId, ExampleDirectoryId)
                             .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                             .either
    } yield assert(result)(isLeft(equalTo(NotAFile(collectionId, ExampleDirectoryId))))
  }

  private val deleteDirectoryTest = testM("Should delete directory recursively") {
    val d1 = ExampleDirectoryMetadata.copy(name = "d1")
    val d2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid1), name = "d2")
    val d2_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid7), name = "d2_1")
    val d2_f1 = ExampleFileMetadata.copy(parentId = Some(d2.id), name = "d2_f1.txt")
    val d1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid3), parentId = Some(d1.id), name = "d1_f1.txt")
    val d1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id), name = "d1_1")
    val d1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid5), parentId = Some(d1_1.id), name = "d1_1_f1.txt")
    val d1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid6), parentId = Some(d1.id), name = "d1_2")
    val d1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid8), parentId = Some(d1_1.id), name = "d1_1_1")
    val d1_1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid9), parentId = Some(d1_1.id), name = "d1_1_2")
    val d1_1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid10), parentId = Some(d1_1_1.id), name = "d1_1_1_f1.txt")
    val d1_1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid11), parentId = Some(d1_1_1.id), name = "d1_1_1_1")
    val d1_1_2_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid12), parentId = Some(d1_1_2.id), name = "d1_1_2_f1.txt")

    val initialFilesRepoState: List[StorageUnit] =
      List(d1, d2, d2_1, d2_f1, d1_f1, d1_1, d1_1_f1, d1_2, d1_1_1, d1_1_2, d1_1_1_f1, d1_1_1_1, d1_1_2_f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteFileOrDirectoryAs(UserId(ExampleUserId), collectionId, d1_1.id, deleteNonEmptyDirs = true)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(equalTo(())) &&
      assert(finalFilesRepoState)(hasSameElements(List(d1, d1_2, d1_f1, d2, d2_1, d2_f1))) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(
        equalTo(
          List(
            StoredTask(
              Task(
                ExampleFuuid1,
                DeleteFiles(
                  List(
                    show"user_files/$ExampleCollectionId/${d1_1_f1.id.show.substring(0, 2)}/${d1_1_f1.id}",
                    show"user_files/$ExampleCollectionId/${d1_1_1_f1.id.show.substring(0, 2)}/${d1_1_1_f1.id}",
                    show"user_files/$ExampleCollectionId/${d1_1_2_f1.id.show.substring(0, 2)}/${d1_1_2_f1.id}"
                  ),
                  recursively = true
                )
              ),
              NotStarted
            )
          )
        )
      )
  }

  private val deleteFileTest = testM("Should delete file") {
    val d1 = ExampleDirectoryMetadata.copy(name = "d1")
    val d2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid1), name = "d2")
    val d2_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid7), name = "d2_1")
    val d2_f1 = ExampleFileMetadata.copy(parentId = Some(d2.id), name = "d2_f1.txt")
    val d1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid3), parentId = Some(d1.id), name = "d1_f1.txt")
    val d1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id), name = "d1_1")
    val d1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid5), parentId = Some(d1_1.id), name = "d1_1_f1.txt")
    val d1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid6), parentId = Some(d1.id), name = "d1_2")
    val d1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid8), parentId = Some(d1_1.id), name = "d1_1_1")
    val d1_1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid9), parentId = Some(d1_1.id), name = "d1_1_2")
    val d1_1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid10), parentId = Some(d1_1_1.id), name = "d1_1_1_f1.txt")
    val d1_1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid11), parentId = Some(d1_1_1.id), name = "d1_1_1_1")
    val d1_1_2_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid12), parentId = Some(d1_1_2.id), name = "d1_1_2_f1.txt")

    val initialFilesRepoState: List[StorageUnit] =
      List(d1, d2, d2_1, d2_f1, d1_f1, d1_1, d1_1_f1, d1_2, d1_1_1, d1_1_2, d1_1_1_f1, d1_1_1_1, d1_1_2_f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteFileOrDirectoryAs(UserId(ExampleUserId), collectionId, d1_1_f1.id, deleteNonEmptyDirs = true)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(equalTo(())) &&
      assert(finalFilesRepoState)(hasSameElements(initialFilesRepoState.filterNot(_.id === d1_1_f1.id))) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(
        equalTo(
          List(
            StoredTask(
              Task(
                ExampleFuuid1,
                DeleteFiles(
                  List(
                    show"user_files/$ExampleCollectionId/${d1_1_f1.id.show.substring(0, 2)}/${d1_1_f1.id}"
                  ),
                  recursively = true
                )
              ),
              NotStarted
            )
          )
        )
      )
  }

  private val deleteEmptyDirectoryTest = testM("Should delete empty directory non recursively") {
    val d1 = ExampleDirectoryMetadata.copy(name = "d1")
    val d2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid1), name = "d2")
    val d2_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid7), name = "d2_1")
    val d2_f1 = ExampleFileMetadata.copy(parentId = Some(d2.id), name = "d2_f1.txt")
    val d1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid3), parentId = Some(d1.id), name = "d1_f1.txt")
    val d1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id), name = "d1_1")
    val d1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid5), parentId = Some(d1_1.id), name = "d1_1_f1.txt")
    val d1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid6), parentId = Some(d1.id), name = "d1_2")
    val d1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid8), parentId = Some(d1_1.id), name = "d1_1_1")
    val d1_1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid9), parentId = Some(d1_1.id), name = "d1_1_2")
    val d1_1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid10), parentId = Some(d1_1_1.id), name = "d1_1_1_f1.txt")
    val d1_1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid11), parentId = Some(d1_1_1.id), name = "d1_1_1_1")
    val d1_1_2_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid12), parentId = Some(d1_1_2.id), name = "d1_1_2_f1.txt")

    val initialFilesRepoState: List[StorageUnit] =
      List(d1, d2, d2_1, d2_f1, d1_f1, d1_1, d1_1_f1, d1_2, d1_1_1, d1_1_2, d1_1_1_f1, d1_1_1_1, d1_1_2_f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteFileOrDirectoryAs(UserId(ExampleUserId), collectionId, d2_1.id, deleteNonEmptyDirs = false)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(equalTo(())) &&
      assert(finalFilesRepoState)(hasSameElements(initialFilesRepoState.filterNot(_.id === d2_1.id))) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(
        equalTo(
          List(
            StoredTask(
              Task(
                ExampleFuuid1,
                DeleteFiles(
                  List.empty,
                  recursively = true
                )
              ),
              NotStarted
            )
          )
        )
      )
  }

  private val deleteDirectoryForbiddenTest = testM("Should not delete directory if user has no permissions") {
    val d1 = ExampleDirectoryMetadata.copy(name = "d1")
    val d2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid1), name = "d2")
    val d2_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid7), name = "d2_1")
    val d2_f1 = ExampleFileMetadata.copy(parentId = Some(d2.id), name = "d2_f1.txt")
    val d1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid3), parentId = Some(d1.id), name = "d1_f1.txt")
    val d1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id), name = "d1_1")
    val d1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid5), parentId = Some(d1_1.id), name = "d1_1_f1.txt")
    val d1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid6), parentId = Some(d1.id), name = "d1_2")
    val d1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid8), parentId = Some(d1_1.id), name = "d1_1_1")
    val d1_1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid9), parentId = Some(d1_1.id), name = "d1_1_2")
    val d1_1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid10), parentId = Some(d1_1_1.id), name = "d1_1_1_f1.txt")
    val d1_1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid11), parentId = Some(d1_1_1.id), name = "d1_1_1_1")
    val d1_1_2_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid12), parentId = Some(d1_1_2.id), name = "d1_1_2_f1.txt")

    val initialFilesRepoState: List[StorageUnit] =
      List(d1, d2, d2_1, d2_f1, d1_f1, d1_1, d1_1_f1, d1_2, d1_1_1, d1_1_2, d1_1_1_f1, d1_1_1_1, d1_1_2_f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions(permissions = Set.empty)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteFileOrDirectoryAs(UserId(ExampleUserId), collectionId, d1_1.id, deleteNonEmptyDirs = true)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(AuthorizationError(OperationNotPermitted(DeleteFile(Subject(ExampleUserId), collectionId)))))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val deleteDirectoryNotFoundTest = testM("Should not delete directory which not exists") {
    val d1 = ExampleDirectoryMetadata.copy(name = "d1")
    val d2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid1), name = "d2")
    val d2_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid7), name = "d2_1")
    val d2_f1 = ExampleFileMetadata.copy(parentId = Some(d2.id), name = "d2_f1.txt")
    val d1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid3), parentId = Some(d1.id), name = "d1_f1.txt")
    val d1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id), name = "d1_1")
    val d1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid5), parentId = Some(d1_1.id), name = "d1_1_f1.txt")
    val d1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid6), parentId = Some(d1.id), name = "d1_2")
    val d1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid8), parentId = Some(d1_1.id), name = "d1_1_1")
    val d1_1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid9), parentId = Some(d1_1.id), name = "d1_1_2")
    val d1_1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid10), parentId = Some(d1_1_1.id), name = "d1_1_1_f1.txt")
    val d1_1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid11), parentId = Some(d1_1_1.id), name = "d1_1_1_1")
    val d1_1_2_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid12), parentId = Some(d1_1_2.id), name = "d1_1_2_f1.txt")

    val initialFilesRepoState: List[StorageUnit] =
      List(d1, d2, d2_1, d2_f1, d1_f1, d1_1, d1_1_f1, d1_2, d1_1_1, d1_1_2, d1_1_1_f1, d1_1_1_1, d1_1_2_f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteFileOrDirectoryAs(UserId(ExampleUserId), collectionId, FileId(ExampleFuuid13), deleteNonEmptyDirs = true)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(FileNotFound(collectionId, FileId(ExampleFuuid13), None)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val deleteDirectoryWithChildrenTest = testM("Should not delete directory non recursively if children found") {
    val d1 = ExampleDirectoryMetadata.copy(name = "d1")
    val d2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid1), name = "d2")
    val d2_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid7), name = "d2_1")
    val d2_f1 = ExampleFileMetadata.copy(parentId = Some(d2.id), name = "d2_f1.txt")
    val d1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid3), parentId = Some(d1.id), name = "d1_f1.txt")
    val d1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid4), parentId = Some(d1.id), name = "d1_1")
    val d1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid5), parentId = Some(d1_1.id), name = "d1_1_f1.txt")
    val d1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid6), parentId = Some(d1.id), name = "d1_2")
    val d1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid8), parentId = Some(d1_1.id), name = "d1_1_1")
    val d1_1_2 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid9), parentId = Some(d1_1.id), name = "d1_1_2")
    val d1_1_1_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid10), parentId = Some(d1_1_1.id), name = "d1_1_1_f1.txt")
    val d1_1_1_1 = ExampleDirectoryMetadata.copy(id = FileId(ExampleFuuid11), parentId = Some(d1_1_1.id), name = "d1_1_1_1")
    val d1_1_2_f1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid12), parentId = Some(d1_1_2.id), name = "d1_1_2_f1.txt")

    val initialFilesRepoState: List[StorageUnit] =
      List(d1, d2, d2_1, d2_f1, d1_f1, d1_1, d1_1_f1, d1_2, d1_1_1, d1_1_2, d1_1_1_f1, d1_1_1_1, d1_1_2_f1)

    val initialFsState = Map.empty[String, Array[Byte]]

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteFileOrDirectoryAs(UserId(ExampleUserId), collectionId, d1_1.id, deleteNonEmptyDirs = false)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(DirectoryToDeleteContainsChildren(collectionId, d1_1.id)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val deleteVersionTest = testM("Should delete file version") {
    val f1v1 = ExampleFileMetadata.copy(updatedAt = Now)
    val f2v1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), versionId = FileVersionId(ExampleFuuid2), updatedAt = Now)
    val f1v2 = f1v1.copy(versionId = FileVersionId(ExampleFuuid3), updatedAt = Now.plusSeconds(10))
    val f2v2 = f2v1.copy(versionId = FileVersionId(ExampleFuuid4), updatedAt = Now.plusSeconds(10))
    val f1v3 = f1v1.copy(versionId = FileVersionId(ExampleFuuid5), updatedAt = Now.plusSeconds(20))
    val f2v3 = f2v1.copy(versionId = FileVersionId(ExampleFuuid6), updatedAt = Now.plusSeconds(20))

    val initialFilesRepoState: List[StorageUnit] = List(f1v1, f2v1, f1v2, f2v2, f1v3, f2v3)

    val initialFsState =
      Map(show"user_files/$collectionId/${f1v2.id.show.substring(0, 2)}/${f1v2.id}/${f1v2.versionId}" -> ExampleFileContent)

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteVersionAs(UserId(ExampleUserId), collectionId, f1v2.id, f1v2.versionId)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(equalTo(())) &&
      assert(finalFilesRepoState)(hasSameElements(List(f1v1, f2v1, f2v2, f1v3, f2v3))) &&
      assert(finalFsState)(equalTo(Map.empty[String, Array[Byte]])) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val deleteVersionForbiddenTest = testM("Should not delete file version if user has no permissions") {
    val f1v1 = ExampleFileMetadata.copy(updatedAt = Now)
    val f2v1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), versionId = FileVersionId(ExampleFuuid2), updatedAt = Now)
    val f1v2 = f1v1.copy(versionId = FileVersionId(ExampleFuuid3), updatedAt = Now.plusSeconds(10))
    val f2v2 = f2v1.copy(versionId = FileVersionId(ExampleFuuid4), updatedAt = Now.plusSeconds(10))
    val f1v3 = f1v1.copy(versionId = FileVersionId(ExampleFuuid5), updatedAt = Now.plusSeconds(20))
    val f2v3 = f2v1.copy(versionId = FileVersionId(ExampleFuuid6), updatedAt = Now.plusSeconds(20))

    val initialFilesRepoState: List[StorageUnit] = List(f1v1, f2v1, f1v2, f2v2, f1v3, f2v3)

    val initialFsState =
      Map(show"user_files/$collectionId/${f1v2.id.show.substring(0, 2)}/${f1v2.id}/${f1v2.versionId}" -> ExampleFileContent)

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions(permissions = Set.empty)
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteVersionAs(UserId(ExampleUserId), collectionId, f1v2.id, f1v2.versionId)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(AuthorizationError(OperationNotPermitted(DeleteFile(Subject(ExampleUserId), collectionId)))))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val deleteVersionFileNotFoundTest = testM("Should not delete file version if file not found") {
    val f1v1 = ExampleFileMetadata.copy(updatedAt = Now)
    val f2v1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), versionId = FileVersionId(ExampleFuuid2), updatedAt = Now)
    val f1v2 = f1v1.copy(versionId = FileVersionId(ExampleFuuid3), updatedAt = Now.plusSeconds(10))
    val f2v2 = f2v1.copy(versionId = FileVersionId(ExampleFuuid4), updatedAt = Now.plusSeconds(10))
    val f1v3 = f1v1.copy(versionId = FileVersionId(ExampleFuuid5), updatedAt = Now.plusSeconds(20))
    val f2v3 = f2v1.copy(versionId = FileVersionId(ExampleFuuid6), updatedAt = Now.plusSeconds(20))

    val initialFilesRepoState: List[StorageUnit] = List(f1v1, f2v1, f1v2, f2v2, f1v3, f2v3)

    val initialFsState =
      Map(show"user_files/$collectionId/${f1v2.id.show.substring(0, 2)}/${f1v2.id}/${f1v2.versionId}" -> ExampleFileContent)

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteVersionAs(UserId(ExampleUserId), collectionId, FileId(ExampleFuuid7), f1v2.versionId)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(FileNotFound(collectionId, FileId(ExampleFuuid7), None)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val deleteVersionVersionNotFoundTest = testM("Should not delete file version if version not found") {
    val f1v1 = ExampleFileMetadata.copy(updatedAt = Now)
    val f2v1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), versionId = FileVersionId(ExampleFuuid2), updatedAt = Now)
    val f1v2 = f1v1.copy(versionId = FileVersionId(ExampleFuuid3), updatedAt = Now.plusSeconds(10))
    val f2v2 = f2v1.copy(versionId = FileVersionId(ExampleFuuid4), updatedAt = Now.plusSeconds(10))
    val f1v3 = f1v1.copy(versionId = FileVersionId(ExampleFuuid5), updatedAt = Now.plusSeconds(20))
    val f2v3 = f2v1.copy(versionId = FileVersionId(ExampleFuuid6), updatedAt = Now.plusSeconds(20))

    val initialFilesRepoState: List[StorageUnit] = List(f1v1, f2v1, f1v2, f2v2, f1v3, f2v3)

    val initialFsState =
      Map(show"user_files/$collectionId/${f1v2.id.show.substring(0, 2)}/${f1v2.id}/${f1v2.versionId}" -> ExampleFileContent)

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteVersionAs(UserId(ExampleUserId), collectionId, f1v2.id, FileVersionId(ExampleFuuid7))
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(FileNotFound(collectionId, f1v2.id, Some(FileVersionId(ExampleFuuid7)))))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val deleteVersionNotAFileTest = testM("Should not delete version if object is not a file") {
    val f1v1 = ExampleFileMetadata.copy(updatedAt = Now)
    val f2v1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), versionId = FileVersionId(ExampleFuuid2), updatedAt = Now)
    val f1v2 = f1v1.copy(versionId = FileVersionId(ExampleFuuid3), updatedAt = Now.plusSeconds(10))
    val f2v2 = f2v1.copy(versionId = FileVersionId(ExampleFuuid4), updatedAt = Now.plusSeconds(10))
    val f1v3 = f1v1.copy(versionId = FileVersionId(ExampleFuuid5), updatedAt = Now.plusSeconds(20))
    val f2v3 = f2v1.copy(versionId = FileVersionId(ExampleFuuid6), updatedAt = Now.plusSeconds(20))

    val initialFilesRepoState: List[StorageUnit] = List(f1v1, f2v1, f1v2, f2v2, f1v3, f2v3, ExampleDirectoryMetadata)

    val initialFsState =
      Map(show"user_files/$collectionId/${f1v2.id.show.substring(0, 2)}/${f1v2.id}/${f1v2.versionId}" -> ExampleFileContent)

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteVersionAs(UserId(ExampleUserId), collectionId, ExampleDirectoryId, f1v2.versionId)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(NotAFile(collectionId, ExampleDirectoryId)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

  private val deleteLatestVersionTest = testM("Should not delete file version if latest version was provided") {
    val f1v1 = ExampleFileMetadata.copy(updatedAt = Now)
    val f2v1 = ExampleFileMetadata.copy(id = FileId(ExampleFuuid1), versionId = FileVersionId(ExampleFuuid2), updatedAt = Now)
    val f1v2 = f1v1.copy(versionId = FileVersionId(ExampleFuuid3), updatedAt = Now.plusSeconds(10))
    val f2v2 = f2v1.copy(versionId = FileVersionId(ExampleFuuid4), updatedAt = Now.plusSeconds(10))
    val f1v3 = f1v1.copy(versionId = FileVersionId(ExampleFuuid5), updatedAt = Now.plusSeconds(20))
    val f2v3 = f2v1.copy(versionId = FileVersionId(ExampleFuuid6), updatedAt = Now.plusSeconds(20))

    val initialFilesRepoState: List[StorageUnit] = List(f1v1, f2v1, f1v2, f2v2, f1v3, f2v3)

    val initialFsState =
      Map(show"user_files/$collectionId/${f1v2.id.show.substring(0, 2)}/${f1v2.id}/${f1v2.versionId}" -> ExampleFileContent)

    val initialOutboxState = List.empty[StoredTask]

    for {
      filesMetadataRepo   <- Ref.make(initialFilesRepoState)
      collectionsRepo     <- emptyCollectionsRepoWithPermissions()
      fs                  <- Ref.make(initialFsState)
      outbox              <- Ref.make(initialOutboxState)
      result              <- FilesService
                               .deleteVersionAs(UserId(ExampleUserId), collectionId, f1v3.id, f1v3.versionId)
                               .provideLayer(createService(filesMetadataRepo, collectionsRepo, fs, outbox))
                               .either
      finalFilesRepoState <- filesMetadataRepo.get
      finalFsState        <- fs.get
      finalOutboxState    <- outbox.get
    } yield assert(result)(isLeft(equalTo(DeleteLatestVersionImpossibleError(collectionId, f1v3.id, f1v3.versionId)))) &&
      assert(finalFilesRepoState)(equalTo(initialFilesRepoState)) &&
      assert(finalFsState)(equalTo(initialFsState)) &&
      assert(finalOutboxState)(equalTo(initialOutboxState))
  }

}
