package com.github.huronapp.api.domain.files

import cats.syntax.show._
import cats.data.Chain
import io.circe.syntax._
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.auth.authorization.{CreateFile, DeleteFile, ModifyFile, OperationNotPermitted, ReadContent, ReadMetadata}
import com.github.huronapp.api.constants.{Collections, Files, MiscConstants, Users}
import com.github.huronapp.api.domain.collections.CollectionId
import com.github.huronapp.api.domain.files.FilesRoutes.FilesRoutes
import com.github.huronapp.api.domain.files.dto.{
  DirectoryData,
  EncryptedContent,
  FileContentData,
  FileData,
  NewDirectory,
  NewFile,
  NewVersionReq,
  StorageUnitData,
  UpdateStorageUnitMetadataReq,
  VersionData
}
import com.github.huronapp.api.domain.files.dto.fields.{ContentDigest, Description, EncryptedBytes, EncryptedContentAlgorithm, FileName, Iv}
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import com.github.huronapp.api.http.ErrorResponse
import com.github.huronapp.api.testdoubles.FilesServiceStub.FilesServiceResponses
import com.github.huronapp.api.testdoubles.HttpAuthenticationFake.validAuthHeader
import com.github.huronapp.api.testdoubles.{FilesServiceStub, HttpAuthenticationFake, LoggerFake}
import com.github.huronapp.api.utils.OptionalValue
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.{Method, Request, Status}
import org.http4s.implicits._
import zio.interop.catz._
import zio.test.Assertion.equalTo
import zio.{Ref, ZIO, ZLayer}
import zio.test.assert
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec}

object FilesRoutesSpec extends DefaultRunnableSpec with Collections with Files with Users with MiscConstants {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Files routes spec")(
      createDirectoryTest,
      createDirectoryUnauthorizedTest,
      createDirectoryForbiddenTest,
      createDirectoryAlreadyExistsTest,
      createDirectoryParentNotFoundTest,
      createDirectoryParentNotDirectoryTest,
      uploadFileTest,
      uploadFileUnauthorizedTest,
      uploadFileForbiddenTest,
      uploadExistingFileTest,
      uploadFileParentNotFoundTest,
      uploadFileParentNotDirectoryTest,
      uploadFileEncryptionKeyVersionMismatchTest,
      readMetadataTest,
      readMetadataUnauthorizedTest,
      readMetadataForbiddenTest,
      readMetadataFileNotFoundTest,
      readContentTest,
      readContentUnauthorizedTest,
      readContentForbiddenTest,
      readContentFileNotFoundTest,
      readContentNotAFileTest,
      listParentsTest,
      listParentsUnauthorizedTest,
      listParentsForbiddenTest,
      listParentsFileNotFoundTest,
      updateMetadataTest,
      updateMetadataUnauthorizedTest,
      updateMetadataForbiddenTest,
      updateMetadataParentNotDirectoryTest,
      updateMetadataParentNotFoundTest,
      updateMetadataFileExistsTest,
      updateMetadataFileNotFoundTest,
      updateMetadataFileNoUpdatesTest,
      updateMetadataDescriptionAssignedToNonFileTest,
      updateMetadataParentSetToSelfTest,
      updateMetadataCircularParentTest,
      uploadNewVersionTest,
      uploadNewVersionUnauthorizedTest,
      uploadNewVersionForbiddenTest,
      uploadNewVersionFileNotFoundTest,
      uploadNewVersionContentNotChangedTest,
      uploadNewVersionKeyVersionMismatchTest,
      listVersionsTest,
      listVersionsUnauthorizedTest,
      listVersionsForbiddenTest,
      listVersionsFileNotFoundTest,
      listVersionsNotAFileTest,
      deleteFileTest,
      deleteFileUnauthorizedTest,
      deleteFileForbiddenTest,
      deleteFileNotFoundTest,
      deleteDirectoryWithChildrenTest,
      deleteVersionTest,
      deleteVersionUnauthorizedTest,
      deleteVersionForbiddenTest,
      deleteVersionFileNotFoundTest,
      deleteVersionNotAFileTest,
      deleteVersionLatestTest,
      listRootDirectoryChildrenTest,
      listRootDirectoryChildrenUnauthorizedTest,
      listRootDirectoryChildrenForbiddenTest,
      listChildrenTest,
      listChildrenUnauthorizedTest,
      listChildrenForbiddenTest,
      listChildrenParentNotDirectoryTest,
      listChildrenParentNotFoundTest
    )

  private def createRoutes(logs: Ref[Chain[String]], filesServiceResponses: FilesServiceResponses): ZLayer[Any, Nothing, FilesRoutes] =
    LoggerFake.usingRef(logs) ++ HttpAuthenticationFake.create ++ FilesServiceStub.withResponses(filesServiceResponses) >>> FilesRoutes.live

  private val baseUri = uri"/api/v1/collections".addSegment(ExampleCollectionId.show).addSegment("files")

  private val collectionId = CollectionId(ExampleCollectionId)

  private val newDirectoryDto = NewDirectory(None, FileName(ExampleFileName))

  private val newFileDto = NewFile(
    None,
    FileName(ExampleFileName),
    Some(Description(ExampleFileDescription)),
    None,
    EncryptedContent(
      EncryptedContentAlgorithm("AES-CBC"),
      Iv(ExampleFileContentIv),
      ExampleEncryptionKeyVersion,
      EncryptedBytes(ExampleFileContent)
    ),
    ContentDigest("d4d30f6fb447ae477f87c0f4dd100bed16083ae9e5b44d929ff8966c8885878b")
  )

  private val updateMetadataDto = UpdateStorageUnitMetadataReq(
    Some(OptionalValue(Some(ExampleFuuid1))),
    Some(FileName("new-name")),
    Some(OptionalValue(Some(Description(ExampleFileDescription))))
  )

  private val newVersionDto = NewVersionReq(
    None,
    EncryptedContent(
      EncryptedContentAlgorithm("AES-CBC"),
      Iv(ExampleFileContentIv),
      ExampleEncryptionKeyVersion,
      EncryptedBytes(ExampleFileContent)
    ),
    ContentDigest("d4d30f6fb447ae477f87c0f4dd100bed16083ae9e5b44d929ff8966c8885878b")
  )

  private val createDirectoryTest = testM("should generate response for create directory request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri).withHeaders(validAuthHeader).withEntity(newDirectoryDto)
      result <- routes.run(req)
      body   <- result.as[DirectoryData]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(
        equalTo(
          DirectoryData(
            ExampleDirectoryMetadata.id.id,
            ExampleDirectoryMetadata.collectionId.id,
            ExampleDirectoryMetadata.parentId.map(_.id),
            ExampleDirectoryMetadata.name
          )
        )
      )
  }

  private val createDirectoryUnauthorizedTest = testM("should generate response for create directory request when user is unauthorized") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri).withEntity(newDirectoryDto)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Unauthorized]
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(body)(equalTo(ErrorResponse.Unauthorized("Invalid credentials")))
  }

  private val createDirectoryForbiddenTest = testM("should generate response for create directory request when access is forbidden") {
    val responses = FilesServiceResponses(
      createNewDirectory = ZIO.fail(AuthorizationError(OperationNotPermitted(CreateFile(Subject(ExampleUserId), collectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri).withHeaders(validAuthHeader).withEntity(newDirectoryDto)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Forbidden))
  }

  private val createDirectoryAlreadyExistsTest = testM("should generate response for create directory request if name already exists") {
    val responses = FilesServiceResponses(
      createNewDirectory = ZIO.fail(FileAlreadyExists(collectionId, None, ExampleDirectoryName))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri).withHeaders(validAuthHeader).withEntity(newDirectoryDto)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Conflict]
    } yield assert(result.status)(equalTo(Status.Conflict)) &&
      assert(body)(equalTo(ErrorResponse.Conflict(show"File or directory $ExampleDirectoryName already exists")))
  }

  private val createDirectoryParentNotFoundTest = testM("should generate response for create directory request if parent not found") {
    val responses = FilesServiceResponses(
      createNewDirectory = ZIO.fail(ParentNotFound(collectionId, FileId(ExampleFuuid1)))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri).withHeaders(validAuthHeader).withEntity(newDirectoryDto)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(
        equalTo(
          ErrorResponse.PreconditionFailed(
            show"Directory with id $ExampleFuuid1 not found in collection $collectionId",
            Some("ParentNotFound")
          )
        )
      )
  }

  private val createDirectoryParentNotDirectoryTest =
    testM("should generate response for create directory request if parent is not directory") {
      val responses = FilesServiceResponses(
        createNewDirectory = ZIO.fail(ParentIsNotDirectory(collectionId, FileId(ExampleFuuid1)))
      )

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.POST, uri = baseUri).withHeaders(validAuthHeader).withEntity(newDirectoryDto)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.PreconditionFailed]
      } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
        assert(body)(
          equalTo(
            ErrorResponse.PreconditionFailed(
              show"$ExampleFuuid1 from collection $collectionId is not directory",
              Some("NotADirectory")
            )
          )
        )
    }

  private val uploadFileTest = testM("should generate response for upload file request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri).withHeaders(validAuthHeader).withEntity(newFileDto)
      result <- routes.run(req)
      body   <- result.as[FileData]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(
        equalTo(
          FileData(
            ExampleFileMetadata.id.id,
            ExampleFileMetadata.collectionId.id,
            ExampleFileMetadata.parentId.map(_.id),
            ExampleFileMetadata.name,
            ExampleFileMetadata.description,
            ExampleFileVersionId.id,
            Some(ExampleUserId),
            None,
            ExampleFileMetadata.originalDigest,
            ExampleFileMetadata.size,
            ExampleFileMetadata.updatedAt
          )
        )
      )
  }

  private val uploadFileUnauthorizedTest = testM("should generate response for upload file request if user is unauthorized") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri).withEntity(newFileDto)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Unauthorized]
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(body)(equalTo(ErrorResponse.Unauthorized("Invalid credentials")))
  }

  private val uploadFileForbiddenTest = testM("should generate response for upload file request if action is forbidden") {
    val responses = FilesServiceResponses(createNewFile =
      ZIO.fail(AuthorizationError(OperationNotPermitted(CreateFile(Subject(ExampleUserId), collectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri).withHeaders(validAuthHeader).withEntity(newFileDto)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Forbidden))
  }

  private val uploadExistingFileTest = testM("should generate response for upload file request if file already exists") {
    val responses = FilesServiceResponses(createNewFile = ZIO.fail(FileAlreadyExists(collectionId, None, ExampleFileMetadata.name)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri).withHeaders(validAuthHeader).withEntity(newFileDto)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Conflict]
    } yield assert(result.status)(equalTo(Status.Conflict)) &&
      assert(body)(equalTo(ErrorResponse.Conflict(show"File or directory ${ExampleFileMetadata.name} already exists")))
  }

  private val uploadFileParentNotFoundTest = testM("should generate response for upload file request if parent not found") {
    val responses = FilesServiceResponses(createNewFile = ZIO.fail(ParentNotFound(collectionId, ExampleDirectoryMetadata.id)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri)
              .withHeaders(validAuthHeader)
              .withEntity(newFileDto.copy(parent = Some(ExampleDirectoryMetadata.id.id)))
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(
        equalTo(
          ErrorResponse.PreconditionFailed(
            show"Directory with id $ExampleDirectoryId not found in collection $collectionId",
            Some("ParentNotFound")
          )
        )
      )
  }

  private val uploadFileParentNotDirectoryTest = testM("should generate response for upload file request if parent is not directory") {
    val responses = FilesServiceResponses(createNewFile = ZIO.fail(ParentIsNotDirectory(collectionId, ExampleDirectoryId)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri)
              .withHeaders(validAuthHeader)
              .withEntity(newFileDto.copy(parent = Some(ExampleDirectoryMetadata.id.id)))
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(
        equalTo(
          ErrorResponse.PreconditionFailed(
            show"$ExampleDirectoryId from collection $collectionId is not directory",
            Some("NotADirectory")
          )
        )
      )
  }

  private val uploadFileEncryptionKeyVersionMismatchTest =
    testM("should generate response for upload file request if encryptionKeyVersionMismatch") {
      val responses = FilesServiceResponses(createNewFile =
        ZIO.fail(EncryptionKeyVersionMismatch(collectionId, ExampleFuuid1, ExampleEncryptionKeyVersion))
      )

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.POST, uri = baseUri)
                .withHeaders(validAuthHeader)
                .withEntity(newFileDto.copy(content = newFileDto.content.copy(encryptionKeyVersion = ExampleFuuid1)))
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.PreconditionFailed]
      } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
        assert(body)(
          equalTo(
            ErrorResponse.PreconditionFailed(
              show"Encryption key version for collection $collectionId is $ExampleEncryptionKeyVersion, but $ExampleFuuid1 was provided",
              Some("EncryptionKeyVersionMismatch")
            )
          )
        )
    }

  private val readMetadataTest = testM("should generate response for read file metadata request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/metadata")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[FileData]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(
        equalTo(
          FileData(
            ExampleFileMetadata.id.id,
            ExampleFileMetadata.collectionId.id,
            ExampleFileMetadata.parentId.map(_.id),
            ExampleFileMetadata.name,
            ExampleFileMetadata.description,
            ExampleFileVersionId.id,
            Some(ExampleUserId),
            None,
            ExampleFileMetadata.originalDigest,
            ExampleFileMetadata.size,
            ExampleFileMetadata.updatedAt
          )
        )
      )
  }

  private val readMetadataUnauthorizedTest = testM("should generate response for read file metadata request if user is unauthorized") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/metadata"))
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Unauthorized))
  }

  private val readMetadataForbiddenTest = testM("should generate response for read file metadata request if action is forbidden") {
    val responses = FilesServiceResponses(getUnitMetadata =
      ZIO.fail(AuthorizationError(OperationNotPermitted(ReadMetadata(Subject(ExampleUserId), collectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/metadata")).withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Forbidden))
  }

  private val readMetadataFileNotFoundTest = testM("should generate response for read file metadata request if file not found") {
    val responses = FilesServiceResponses(getUnitMetadata = ZIO.fail(FileNotFound(collectionId, ExampleFileId, None)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/metadata")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(
        equalTo(ErrorResponse.NotFound(show"File or directory $ExampleFileId with version latest not found in collection $collectionId"))
      )
  }

  private val readContentTest = testM("should generate response for read content request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/content")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[FileContentData]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body.asJson)(
        equalTo(
          FileContentData(
            EncryptedContent(
              EncryptedContentAlgorithm("AES-CBC"),
              Iv(ExampleFileContentIv),
              ExampleEncryptionKeyVersion,
              EncryptedBytes(ExampleFileContent)
            ),
            ExampleFileMetadata.originalDigest,
            ExampleFileMetadata.name,
            ExampleFileMetadata.mimeType
          ).asJson
        )
      )
  }

  private val readContentUnauthorizedTest = testM("should generate response for read content request if user unauthorized") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/content"))
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Unauthorized))
  }

  private val readContentForbiddenTest = testM("should generate response for read content request if action is forbidden") {
    val responses = FilesServiceResponses(getFileContent =
      ZIO.fail(AuthorizationError(OperationNotPermitted(ReadContent(Subject(ExampleUserId), collectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/content")).withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Forbidden))
  }

  private val readContentFileNotFoundTest = testM("should generate response for read content request if file not found") {
    val responses = FilesServiceResponses(getFileContent = ZIO.fail(FileNotFound(collectionId, ExampleFileId, None)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/content")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound("File or version not found")))
  }

  private val readContentNotAFileTest = testM("should generate response for read content request if object is not a file") {
    val responses = FilesServiceResponses(getFileContent = ZIO.fail(NotAFile(collectionId, ExampleFileId)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/content")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(
        equalTo(ErrorResponse.PreconditionFailed(show"Object $ExampleFileId from collection $collectionId is not a file", Some("NotAFile")))
      )
  }

  private val listParentsTest = testM("should generate response for list parents request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/parents")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[List[DirectoryData]]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(
        equalTo(
          List(
            DirectoryData(
              ExampleDirectoryId.id,
              ExampleDirectoryMetadata.collectionId.id,
              ExampleDirectoryMetadata.parentId.map(_.id),
              ExampleDirectoryMetadata.name
            )
          )
        )
      )
  }

  private val listParentsUnauthorizedTest = testM("should generate response for list parents request if user is unauthorized") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/parents"))
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Unauthorized))
  }

  private val listParentsForbiddenTest = testM("should generate response for list parents request if action is forbidden") {
    val responses = FilesServiceResponses(listParents =
      ZIO.fail(AuthorizationError(OperationNotPermitted(ReadMetadata(Subject(ExampleUserId), collectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/parents")).withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Forbidden))
  }

  private val listParentsFileNotFoundTest = testM("should generate response for list parents request if file not found") {
    val responses = FilesServiceResponses(listParents = ZIO.fail(FileNotFound(collectionId, ExampleFileId, None)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/parents")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound("File not found")))
  }

  private val updateMetadataTest = testM("should generate response for update metadata request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.PATCH, uri = baseUri.addPath(show"$ExampleFileId"))
              .withEntity(updateMetadataDto)
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[StorageUnitData]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(
        equalTo(
          FileData(
            ExampleFileMetadata.id.id,
            ExampleFileMetadata.collectionId.id,
            ExampleFileMetadata.parentId.map(_.id),
            ExampleFileMetadata.name,
            ExampleFileMetadata.description,
            ExampleFileVersionId.id,
            Some(ExampleUserId),
            None,
            ExampleFileMetadata.originalDigest,
            ExampleFileMetadata.size,
            ExampleFileMetadata.updatedAt
          )
        )
      )
  }

  private val updateMetadataUnauthorizedTest = testM("should generate response for update metadata request if user is unauthorized") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.PATCH, uri = baseUri.addPath(show"$ExampleFileId")).withEntity(updateMetadataDto)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Unauthorized))
  }

  private val updateMetadataForbiddenTest = testM("should generate response for update metadata request if action is forbidden") {
    val responses = FilesServiceResponses(updateMetadata =
      ZIO.fail(AuthorizationError(OperationNotPermitted(ModifyFile(Subject(ExampleUserId), collectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.PATCH, uri = baseUri.addPath(show"$ExampleFileId"))
              .withEntity(updateMetadataDto)
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Forbidden))
  }

  private val updateMetadataParentNotDirectoryTest =
    testM("should generate response for update metadata request if parent is not directory") {
      val responses = FilesServiceResponses(updateMetadata = ZIO.fail(ParentIsNotDirectory(collectionId, ExampleDirectoryId)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.PATCH, uri = baseUri.addPath(show"$ExampleFileId"))
                .withEntity(updateMetadataDto)
                .withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.PreconditionFailed]
      } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
        assert(body)(
          equalTo(
            ErrorResponse.PreconditionFailed(
              show"$ExampleDirectoryId from collection $collectionId is not directory",
              Some("NotADirectory")
            )
          )
        )
    }

  private val updateMetadataParentNotFoundTest =
    testM("should generate response for update metadata request if parent not found") {
      val responses = FilesServiceResponses(updateMetadata = ZIO.fail(ParentNotFound(collectionId, ExampleDirectoryId)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.PATCH, uri = baseUri.addPath(show"$ExampleFileId"))
                .withEntity(updateMetadataDto)
                .withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.PreconditionFailed]
      } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
        assert(body)(
          equalTo(
            ErrorResponse.PreconditionFailed(
              show"Directory with id $ExampleDirectoryId not found in collection $collectionId",
              Some("ParentNotFound")
            )
          )
        )
    }

  private val updateMetadataFileExistsTest =
    testM("should generate response for update metadata request if file already exists") {
      val responses =
        FilesServiceResponses(updateMetadata = ZIO.fail(FileAlreadyExists(collectionId, Some(ExampleDirectoryId), ExampleFileName)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.PATCH, uri = baseUri.addPath(show"$ExampleFileId"))
                .withEntity(updateMetadataDto)
                .withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.Conflict]
      } yield assert(result.status)(equalTo(Status.Conflict)) &&
        assert(body)(equalTo(ErrorResponse.Conflict(show"File or directory $ExampleFileName already exists")))
    }

  private val updateMetadataFileNotFoundTest =
    testM("should generate response for update metadata request if file not found") {
      val responses =
        FilesServiceResponses(updateMetadata = ZIO.fail(FileNotFound(collectionId, ExampleFileId, None)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.PATCH, uri = baseUri.addPath(show"$ExampleFileId"))
                .withEntity(updateMetadataDto)
                .withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.NotFound]
      } yield assert(result.status)(equalTo(Status.NotFound)) &&
        assert(body)(equalTo(ErrorResponse.NotFound("File or directory not found")))
    }

  private val updateMetadataFileNoUpdatesTest =
    testM("should generate response for update metadata request if no updates provided") {
      val responses =
        FilesServiceResponses(updateMetadata =
          ZIO.fail(NoUpdates(collectionId, "file metadata", ExampleFileId.id, UpdateStorageUnitMetadataReq(None, None, None)))
        )

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.PATCH, uri = baseUri.addPath(show"$ExampleFileId"))
                .withEntity(UpdateStorageUnitMetadataReq(None, None, None))
                .withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.BadRequest]
      } yield assert(result.status)(equalTo(Status.BadRequest)) &&
        assert(body)(equalTo(ErrorResponse.BadRequest("No updates in request")))
    }

  private val updateMetadataDescriptionAssignedToNonFileTest =
    testM("should generate response for update metadata request if description assigned to non file object") {
      val responses = FilesServiceResponses(updateMetadata = ZIO.fail(DescriptionAssignedToNonFileObject(collectionId, ExampleFileId)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.PATCH, uri = baseUri.addPath(show"$ExampleFileId"))
                .withEntity(updateMetadataDto)
                .withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.BadRequest]
      } yield assert(result.status)(equalTo(Status.BadRequest)) &&
        assert(body)(equalTo(ErrorResponse.BadRequest("Description can be assigned only to file")))
    }

  private val updateMetadataParentSetToSelfTest =
    testM("should generate response for update metadata request if new parent is set to self") {
      val responses =
        FilesServiceResponses(updateMetadata = ZIO.fail(NewParentSetToSelf(collectionId, ExampleFileId)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.PATCH, uri = baseUri.addPath(show"$ExampleFileId"))
                .withEntity(updateMetadataDto.copy(parent = Some(OptionalValue(Some(ExampleFileId.id)))))
                .withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.BadRequest]
      } yield assert(result.status)(equalTo(Status.BadRequest)) &&
        assert(body)(equalTo(ErrorResponse.BadRequest("New parent set to self")))
    }

  private val updateMetadataCircularParentTest =
    testM("should generate response for update metadata request if circular parent found") {
      val responses =
        FilesServiceResponses(updateMetadata = ZIO.fail(CircularParentSet(collectionId, ExampleFileId, ExampleDirectoryId)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.PATCH, uri = baseUri.addPath(show"$ExampleFileId"))
                .withEntity(updateMetadataDto)
                .withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.PreconditionFailed]
      } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
        assert(body)(
          equalTo(
            ErrorResponse.PreconditionFailed(
              show"Directory $ExampleDirectoryId from collection $collectionId already has $ExampleFileId in his parents",
              Some("CircularParents")
            )
          )
        )
    }

  private val uploadNewVersionTest = testM("should generate response for upload new version request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri.addPath(show"$ExampleFileId/versions"))
              .withEntity(newVersionDto)
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[FileData]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(
        equalTo(
          FileData(
            ExampleFileMetadata.id.id,
            ExampleFileMetadata.collectionId.id,
            ExampleFileMetadata.parentId.map(_.id),
            ExampleFileMetadata.name,
            ExampleFileMetadata.description,
            ExampleFileVersionId.id,
            Some(ExampleUserId),
            None,
            ExampleFileMetadata.originalDigest,
            ExampleFileMetadata.size,
            ExampleFileMetadata.updatedAt
          )
        )
      )
  }

  private val uploadNewVersionUnauthorizedTest = testM("should generate response for upload new version request if user is unauthorized") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri.addPath(show"$ExampleFileId/versions")).withEntity(newVersionDto)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Unauthorized))
  }

  private val uploadNewVersionForbiddenTest = testM("should generate response for upload new version request if action is forbidden") {
    val responses = FilesServiceResponses(addVersion =
      ZIO.fail(AuthorizationError(OperationNotPermitted(CreateFile(Subject(ExampleUserId), collectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri.addPath(show"$ExampleFileId/versions"))
              .withEntity(newVersionDto)
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Forbidden))
  }

  private val uploadNewVersionFileNotFoundTest = testM("should generate response for upload new version request if file not found") {
    val responses = FilesServiceResponses(addVersion = ZIO.fail(FileNotFound(collectionId, ExampleFileId, None)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri = baseUri.addPath(show"$ExampleFileId/versions"))
              .withEntity(newVersionDto)
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound("File not found")))
  }

  private val uploadNewVersionContentNotChangedTest =
    testM("should generate response for upload new version request if content has not changed") {
      val responses = FilesServiceResponses(addVersion = ZIO.fail(FileContentNotChanged(collectionId, ExampleFileId)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.POST, uri = baseUri.addPath(show"$ExampleFileId/versions"))
                .withEntity(newVersionDto)
                .withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.UnprocessableEntity]
      } yield assert(result.status)(equalTo(Status.UnprocessableEntity)) &&
        assert(body)(equalTo(ErrorResponse.UnprocessableEntity("File content was not changed", Some("FileContentNotChanged"))))
    }

  private val uploadNewVersionKeyVersionMismatchTest =
    testM("should generate response for upload new version request if encryption key version mismatch") {
      val responses =
        FilesServiceResponses(addVersion = ZIO.fail(EncryptionKeyVersionMismatch(collectionId, ExampleEncryptionKeyVersion, ExampleFuuid1)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.POST, uri = baseUri.addPath(show"$ExampleFileId/versions"))
                .withEntity(newVersionDto)
                .withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.PreconditionFailed]
      } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
        assert(body)(
          equalTo(
            ErrorResponse.PreconditionFailed(
              show"Encryption key version for collection $collectionId is $ExampleFuuid1, but $ExampleEncryptionKeyVersion was provided",
              Some("EncryptionKeyVersionMismatch")
            )
          )
        )
    }

  private val listVersionsTest = testM("should generate response for list versions request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/versions")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[List[VersionData]]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(
        equalTo(
          List(
            VersionData(
              ExampleFileMetadata.id.id,
              ExampleFileMetadata.collectionId.id,
              ExampleFileVersionId.id,
              Some(ExampleUserId),
              None,
              ExampleFileMetadata.originalDigest,
              ExampleFileMetadata.size,
              ExampleFileMetadata.updatedAt
            )
          )
        )
      )
  }

  private val listVersionsUnauthorizedTest = testM("should generate response for list versions request if user is not authorized") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/versions"))
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Unauthorized))
  }

  private val listVersionsForbiddenTest = testM("should generate response for list versions request if action is forbidden") {
    val responses = FilesServiceResponses(listVersions =
      ZIO.fail(AuthorizationError(OperationNotPermitted(ReadMetadata(Subject(ExampleUserId), collectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/versions")).withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Forbidden))
  }

  private val listVersionsFileNotFoundTest = testM("should generate response for list versions request if file not found") {
    val responses = FilesServiceResponses(listVersions = ZIO.fail(FileNotFound(collectionId, ExampleFileId, None)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/versions")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound("File not found")))
  }

  private val listVersionsNotAFileTest = testM("should generate response for list versions request if object is not a file") {
    val responses = FilesServiceResponses(listVersions = ZIO.fail(NotAFile(collectionId, ExampleFileId)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleFileId/versions")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(
        equalTo(ErrorResponse.PreconditionFailed(show"Object $ExampleFileId from collection $collectionId is not a file", Some("NotAFile")))
      )
  }

  private val deleteFileTest = testM("should generate response for delete file request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.DELETE, uri = baseUri.addPath(show"$ExampleFileId")).withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.NoContent))
  }

  private val deleteFileUnauthorizedTest = testM("should generate response for delete file request if user is unauthorized") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.DELETE, uri = baseUri.addPath(show"$ExampleFileId"))
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Unauthorized))
  }

  private val deleteFileForbiddenTest = testM("should generate response for delete file request if action is forbidden") {
    val responses = FilesServiceResponses(deleteFile =
      ZIO.fail(AuthorizationError(OperationNotPermitted(DeleteFile(Subject(ExampleUserId), collectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.DELETE, uri = baseUri.addPath(show"$ExampleFileId")).withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Forbidden))
  }

  private val deleteFileNotFoundTest = testM("should generate response for delete file request if file not exists") {
    val responses = FilesServiceResponses(deleteFile = ZIO.fail(FileNotFound(collectionId, ExampleFileId, None)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.DELETE, uri = baseUri.addPath(show"$ExampleFileId")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound("File or directory not found")))
  }

  private val deleteDirectoryWithChildrenTest = testM("should generate response for delete directory which contains children") {
    val responses = FilesServiceResponses(deleteFile = ZIO.fail(DirectoryToDeleteContainsChildren(collectionId, ExampleDirectoryId)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.DELETE, uri = baseUri.addPath(show"$ExampleDirectoryId")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(equalTo(ErrorResponse.PreconditionFailed("Unable to delete directory containing elements", Some("RecursivelyDelete"))))
  }

  private val deleteVersionTest = testM("should generate response for delete version request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.DELETE, uri = baseUri.addPath(show"$ExampleFileId/versions/$ExampleFileVersionId"))
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.NoContent))
  }

  private val deleteVersionUnauthorizedTest = testM("should generate response for delete version request if user is unauthorized") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.DELETE, uri = baseUri.addPath(show"$ExampleFileId/versions/$ExampleFileVersionId"))
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Unauthorized))
  }

  private val deleteVersionForbiddenTest = testM("should generate response for delete version request if action is forbidden") {
    val responses = FilesServiceResponses(deleteVersion =
      ZIO.fail(AuthorizationError(OperationNotPermitted(DeleteFile(Subject(ExampleUserId), collectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.DELETE, uri = baseUri.addPath(show"$ExampleFileId/versions/$ExampleFileVersionId"))
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Forbidden))
  }

  private val deleteVersionFileNotFoundTest = testM("should generate response for delete version request if file or version not found") {
    val responses = FilesServiceResponses(deleteVersion = ZIO.fail(FileNotFound(collectionId, ExampleFileId, Some(ExampleFileVersionId))))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.DELETE, uri = baseUri.addPath(show"$ExampleFileId/versions/$ExampleFileVersionId"))
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound("File or version not found")))
  }

  private val deleteVersionNotAFileTest = testM("should generate response for delete version request if object is not a file") {
    val responses = FilesServiceResponses(deleteVersion = ZIO.fail(NotAFile(collectionId, ExampleFileId)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.DELETE, uri = baseUri.addPath(show"$ExampleFileId/versions/$ExampleFileVersionId"))
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(
        equalTo(ErrorResponse.PreconditionFailed(show"Object $ExampleFileId from collection $collectionId is not a file", Some("NotAFile")))
      )
  }

  private val deleteVersionLatestTest = testM("should generate response for delete version request if selected version is the later one") {
    val responses =
      FilesServiceResponses(deleteVersion = ZIO.fail(DeleteLatestVersionImpossibleError(collectionId, ExampleFileId, ExampleFileVersionId)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.DELETE, uri = baseUri.addPath(show"$ExampleFileId/versions/$ExampleFileVersionId"))
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(
        equalTo(ErrorResponse.PreconditionFailed("Impossible to delete latest version", Some("DeleteLatestVersion")))
      )
  }

  private val listRootDirectoryChildrenTest = testM("should generate response for list children of the root directory request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"children")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[List[FileData]]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(
        equalTo(
          List(
            FileData(
              ExampleFileMetadata.id.id,
              ExampleFileMetadata.collectionId.id,
              ExampleFileMetadata.parentId.map(_.id),
              ExampleFileMetadata.name,
              ExampleFileMetadata.description,
              ExampleFileVersionId.id,
              Some(ExampleUserId),
              None,
              ExampleFileMetadata.originalDigest,
              ExampleFileMetadata.size,
              ExampleFileMetadata.updatedAt
            )
          )
        )
      )
  }

  private val listRootDirectoryChildrenUnauthorizedTest =
    testM("should generate response for list children of the root directory request if user is unauthorized") {
      val responses = FilesServiceResponses()

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"children"))
        result <- routes.run(req)
      } yield assert(result.status)(equalTo(Status.Unauthorized))
    }

  private val listRootDirectoryChildrenForbiddenTest =
    testM("should generate response for list children of the root directory request if action is forbidden") {
      val responses = FilesServiceResponses(listChildren =
        ZIO.fail(AuthorizationError(OperationNotPermitted(ReadMetadata(Subject(ExampleUserId), collectionId))))
      )

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"children")).withHeaders(validAuthHeader)
        result <- routes.run(req)
      } yield assert(result.status)(equalTo(Status.Forbidden))
    }

  private val listChildrenTest = testM("should generate response for list children request") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req =
        Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleDirectoryId/children")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[List[FileData]]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(
        equalTo(
          List(
            FileData(
              ExampleFileMetadata.id.id,
              ExampleFileMetadata.collectionId.id,
              ExampleFileMetadata.parentId.map(_.id),
              ExampleFileMetadata.name,
              ExampleFileMetadata.description,
              ExampleFileVersionId.id,
              Some(ExampleUserId),
              None,
              ExampleFileMetadata.originalDigest,
              ExampleFileMetadata.size,
              ExampleFileMetadata.updatedAt
            )
          )
        )
      )
  }

  private val listChildrenUnauthorizedTest = testM("should generate response for list children request if user is unauthorized") {
    val responses = FilesServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleDirectoryId/children"))
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Unauthorized))
  }

  private val listChildrenForbiddenTest = testM("should generate response for list children request if user is forbidden") {
    val responses = FilesServiceResponses(listChildren =
      ZIO.fail(AuthorizationError(OperationNotPermitted(ReadMetadata(Subject(ExampleUserId), collectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req =
        Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleDirectoryId/children")).withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assert(result.status)(equalTo(Status.Forbidden))
  }

  private val listChildrenParentNotDirectoryTest = testM("should generate response for list children request if parent is not directory") {
    val responses = FilesServiceResponses(listChildren = ZIO.fail(ParentIsNotDirectory(collectionId, ExampleDirectoryId)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req =
        Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleDirectoryId/children")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(
        equalTo(
          ErrorResponse.PreconditionFailed(show"$ExampleDirectoryId from collection $collectionId is not directory", Some("NotADirectory"))
        )
      )
  }

  private val listChildrenParentNotFoundTest = testM("should generate response for list children request if parent not found") {
    val responses = FilesServiceResponses(listChildren = ZIO.fail(ParentNotFound(collectionId, ExampleDirectoryId)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- FilesRoutes.routes.provideLayer(createRoutes(logs, responses)).map(_.orNotFound)
      req =
        Request[RouteEffect](method = Method.GET, uri = baseUri.addPath(show"$ExampleDirectoryId/children")).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(
        equalTo(
          ErrorResponse.NotFound("Directory not found")
        )
      )
  }

}
