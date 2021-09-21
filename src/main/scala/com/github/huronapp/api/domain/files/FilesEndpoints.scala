package com.github.huronapp.api.domain.files

import cats.data.NonEmptyList
import cats.syntax.show._
import com.github.huronapp.api.auth.authentication.AuthenticationInputs
import com.github.huronapp.api.auth.authentication.TapirAuthenticationInputs.authRequestParts
import com.github.huronapp.api.domain.collections.CollectionId
import com.github.huronapp.api.domain.files.dto.fields.{ContentDigest, EncryptedBytes, EncryptedContentAlgorithm, FileName, Iv, MimeType}
import com.github.huronapp.api.domain.files.dto.{
  DirectoryData,
  EncryptedContent,
  FileContentData,
  FileData,
  NewDirectory,
  NewFile,
  NewStorageUnitReq,
  NewVersionReq,
  StorageUnitData,
  UpdateStorageUnitMetadataReq
}
import com.github.huronapp.api.http.{BaseEndpoint, ErrorResponse}
import com.github.huronapp.api.utils.Implicits.collectionId._
import com.github.huronapp.api.utils.Implicits.fileId._
import com.github.huronapp.api.utils.Implicits.versionId._
import com.github.huronapp.api.utils.OptionalValue
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Greater
import io.chrisdavenport.fuuid.FUUID
import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.model.StatusCode
import sttp.tapir.codec.refined._
import sttp.tapir.Endpoint
import sttp.tapir.EndpointIO.Example
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._

import java.time.Instant

object FilesEndpoints extends BaseEndpoint {

  private val filesEndpoint: ZEndpoint[CollectionId, Unit, Unit] =
    apiEndpoint.tag("files").in("collections" / path[CollectionId]("collectionId") / "files")

  object Responses {

    def parentNotFound(collectionId: CollectionId, parentId: FileId): ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed(show"Directory with id $parentId not found in collection $collectionId", Some("ParentNotFound"))

    def notDirectory(collectionId: CollectionId, objectId: FileId): ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed(show"$objectId from collection $collectionId is not directory", Some("NotADirectory"))

    def objectIsNotAFile(collectionId: CollectionId, fileId: FileId): ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed(show"Object $fileId from collection $collectionId is not a file", Some("NotAFile"))

    def circularParent(collectionId: CollectionId, fileId: FileId, newParentId: FileId): ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed(
        show"Directory $newParentId from collection $collectionId already has $fileId in his parents",
        Some("CircularParents")
      )

    val fileContentNotChanged: ErrorResponse.UnprocessableEntity =
      ErrorResponse.UnprocessableEntity("File content was not changed", Some("FileContentNotChanged"))

    val deleteLatestVersion: ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed("Impossible to delete latest version", Some("DeleteLatestVersion"))

    def encryptionKeyVersionMismatch(
      collectionId: CollectionId,
      providedVersion: FUUID,
      currentVersion: FUUID
    ): ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed(
        show"Encryption key version for collection $collectionId is $currentVersion, but $providedVersion was provided",
        Some("EncryptionKeyVersionMismatch")
      )

    val recursivelyDeleteForbidden: ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed("Unable to delete directory containing elements", Some("RecursivelyDelete"))

  }

  private val exampleCollectionId = CollectionId(FUUID.fuuid("de3ad125-f16f-49ef-81d4-7a0d17b2a73b"))

  private val exampleFileId = FileId(FUUID.fuuid("6b789c88-cbe0-4e39-9922-77f1fd1dba9a"))

  private val exampleFileId2 = FileId(FUUID.fuuid("ab1e68e7-dc77-41a3-82e1-847a6ae9627a"))

  private val exampleFileContent =
    EncryptedContent(
      EncryptedContentAlgorithm("AES-CBC"),
      Iv("7704bf1f2db3c8ff60d8e72630198c6d"),
      FUUID.fuuid("355cec07-a568-4417-927a-a3ff699c30cc"),
      EncryptedBytes(Array(64, 120))
    )

  private val newDirectoryBodyExample =
    Example(NewDirectory(Some(exampleFileId.id), FileName("example dir")), Some("New directory"), None)

  private val newFileBodyExample =
    Example(
      NewFile(
        Some(exampleFileId.id),
        FileName("file2.txt"),
        Some(MimeType("text/plain")),
        exampleFileContent,
        ContentDigest("7d865e959b2466918c9863afca942d0fb89d7c9ac0c99bafc3749504ded97730")
      ),
      Some("New file"),
      None
    )

  private val directoryDataExample = Example(
    DirectoryData(
      FUUID.fuuid("0de12dfc-1e3e-43cc-ba4f-4af0c91e7d55"),
      exampleCollectionId.id,
      Some(exampleFileId.id),
      "example dir"
    ),
    Some("Directory data"),
    None
  )

  private val fileDataExample = Example(
    FileData(
      FUUID.fuuid("0de12dfc-1e3e-43cc-ba4f-4af0c91e7d55"),
      exampleCollectionId.id,
      Some(exampleFileId.id),
      "file1.txt",
      FUUID.fuuid("e21bf5e1-8b72-463e-bda2-073a665d8c2d"),
      Some(FUUID.fuuid("47aa22cf-d731-48dd-87a4-c72629f51f20")),
      Some("text/plain"),
      "7d865e959b2466918c9863afca942d0fb89d7c9ac0c99bafc3749504ded97730",
      Instant.EPOCH
    ),
    Some("File data"),
    None
  )

  private val parentNotFoundExample = Example(
    Responses.parentNotFound(exampleCollectionId, exampleFileId),
    Responses.parentNotFound(exampleCollectionId, exampleFileId).reason,
    Responses.parentNotFound(exampleCollectionId, exampleFileId).reason
  )

  private val notDirectoryExample = Example(
    Responses.notDirectory(exampleCollectionId, exampleFileId),
    Responses.notDirectory(exampleCollectionId, exampleFileId).reason,
    Responses.notDirectory(exampleCollectionId, exampleFileId).reason
  )

  private val notAFileExample = Example(
    Responses.objectIsNotAFile(exampleCollectionId, exampleFileId),
    Responses.objectIsNotAFile(exampleCollectionId, exampleFileId).reason,
    Responses.objectIsNotAFile(exampleCollectionId, exampleFileId).reason
  )

  private val circularParentExample = Example(
    Responses.circularParent(exampleCollectionId, exampleFileId, exampleFileId2),
    Responses.circularParent(exampleCollectionId, exampleFileId, exampleFileId2).reason,
    Responses.circularParent(exampleCollectionId, exampleFileId, exampleFileId2).reason
  )

  private val contentNotChangedExample = Example(
    Responses.fileContentNotChanged,
    Responses.fileContentNotChanged.reason,
    Responses.fileContentNotChanged.reason
  )

  private val encryptionKeyVersionMismatchExample = Example(
    Responses.encryptionKeyVersionMismatch(
      exampleCollectionId,
      FUUID.fuuid("97663277-2417-4237-ade3-b1706eb57b59"),
      FUUID.fuuid("122366f2-3ae4-4db9-b052-519894c11713")
    ),
    Responses
      .encryptionKeyVersionMismatch(
        exampleCollectionId,
        FUUID.fuuid("97663277-2417-4237-ade3-b1706eb57b59"),
        FUUID.fuuid("122366f2-3ae4-4db9-b052-519894c11713")
      )
      .reason,
    Responses
      .encryptionKeyVersionMismatch(
        exampleCollectionId,
        FUUID.fuuid("97663277-2417-4237-ade3-b1706eb57b59"),
        FUUID.fuuid("122366f2-3ae4-4db9-b052-519894c11713")
      )
      .reason
  )

  private val recursivelyDeleteForbiddenExample = Example(
    Responses.recursivelyDeleteForbidden,
    Responses.recursivelyDeleteForbidden.reason,
    Responses.recursivelyDeleteForbidden.reason
  )

  private val deleteLatestVersionExample =
    Example(Responses.deleteLatestVersion, Responses.deleteLatestVersion.reason, Responses.deleteLatestVersion.reason)

  val createStorageUnitEndpoint: Endpoint[
    (AuthenticationInputs, CollectionId, NewStorageUnitReq),
    ErrorResponse,
    StorageUnitData,
    ZioStreams with capabilities.WebSockets
  ] =
    filesEndpoint
      .summary("Create new directory or upload file")
      .post
      .prependIn(authRequestParts)
      .in(jsonBody[NewStorageUnitReq].examples(List(newDirectoryBodyExample, newFileBodyExample)))
      .out(jsonBody[StorageUnitData].examples(List(directoryDataExample, fileDataExample)))
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfMapping(StatusCode.Conflict, jsonBody[ErrorResponse.Conflict].description("File or directory already exists")),
          oneOfMapping(
            StatusCode.PreconditionFailed,
            jsonBody[ErrorResponse.PreconditionFailed]
              .examples(List(parentNotFoundExample, notDirectoryExample, encryptionKeyVersionMismatchExample))
          )
        )
      )

  val getStorageUnitEndpoint: Endpoint[
    (AuthenticationInputs, CollectionId, FileId, Option[FileVersionId]),
    ErrorResponse,
    StorageUnitData,
    ZioStreams with capabilities.WebSockets
  ] =
    filesEndpoint
      .summary("Get file or directory metadata")
      .get
      .prependIn(authRequestParts)
      .in(path[FileId]("fileId") / "metadata")
      .in(query[Option[FileVersionId]]("versionId"))
      .out(jsonBody[StorageUnitData].examples(List(directoryDataExample, fileDataExample)))
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound])
        )
      )

  val getFileContent: Endpoint[
    (AuthenticationInputs, CollectionId, FileId, Option[FileVersionId]),
    ErrorResponse,
    FileContentData,
    ZioStreams with capabilities.WebSockets
  ] =
    filesEndpoint
      .summary("Get file content")
      .get
      .prependIn(authRequestParts)
      .in(path[FileId]("fileId") / "content")
      .in(query[Option[FileVersionId]]("versionId"))
      .out(
        jsonBody[FileContentData].example(
          FileContentData(
            exampleFileContent,
            "7d865e959b2466918c9863afca942d0fb89d7c9ac0c99bafc3749504ded97730"
          )
        )
      )
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound]),
          oneOfMapping(StatusCode.PreconditionFailed, jsonBody[ErrorResponse.PreconditionFailed].examples(List(notAFileExample)))
        )
      )

  val getParentsEndpoint: Endpoint[(AuthenticationInputs, CollectionId, FileId, Option[Refined[Int, Greater[0]]]), ErrorResponse, List[
    DirectoryData
  ], ZioStreams with capabilities.WebSockets] =
    filesEndpoint
      .summary("Get parents of file or directory")
      .get
      .prependIn(authRequestParts)
      .in(path[FileId]("fileId") / "parents")
      .in(query[Option[Int Refined Greater[0]]]("depth"))
      .out(jsonBody[List[DirectoryData]].example(directoryDataExample.map(List(_))))
      .errorOut(
        oneOf[ErrorResponse](badRequest, unauthorized, forbidden, oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound]))
      )

  val getRootChildrenEndpoint
    : Endpoint[(AuthenticationInputs, CollectionId), ErrorResponse, List[StorageUnitData], ZioStreams with capabilities.WebSockets] =
    filesEndpoint
      .summary("Get direct children of the root directory")
      .get
      .prependIn(authRequestParts)
      .in("children")
      .out(jsonBody[List[StorageUnitData]])
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound])
        )
      )

  val getChildrenEndpoint: Endpoint[(AuthenticationInputs, CollectionId, FileId), ErrorResponse, List[
    StorageUnitData
  ], ZioStreams with capabilities.WebSockets] = filesEndpoint
    .summary("Get direct children of directory")
    .get
    .prependIn(authRequestParts)
    .in(path[FileId]("fileId") / "children")
    .out(jsonBody[List[StorageUnitData]])
    .errorOut(
      oneOf[ErrorResponse](
        badRequest,
        unauthorized,
        forbidden,
        oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound]),
        oneOfMapping(StatusCode.PreconditionFailed, jsonBody[ErrorResponse.PreconditionFailed].examples(List(notDirectoryExample)))
      )
    )

  val updateMetadataEndpoint: Endpoint[
    (AuthenticationInputs, CollectionId, FileId, UpdateStorageUnitMetadataReq),
    ErrorResponse,
    StorageUnitData,
    ZioStreams with capabilities.WebSockets
  ] =
    filesEndpoint
      .summary("Update metadata")
      .patch
      .prependIn(authRequestParts)
      .in(path[FileId]("fileId"))
      .in(
        jsonBody[UpdateStorageUnitMetadataReq].example(
          UpdateStorageUnitMetadataReq(Some(OptionalValue[FUUID](Some(exampleFileId.id))), Some(FileName("example.txt")))
        )
      )
      .out(jsonBody[StorageUnitData].examples(List(directoryDataExample, fileDataExample)))
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound]),
          oneOfMapping(
            StatusCode.Conflict,
            jsonBody[ErrorResponse.Conflict].description("File or directory already exists in new location")
          ),
          oneOfMapping(
            StatusCode.PreconditionFailed,
            jsonBody[ErrorResponse.PreconditionFailed]
              .examples(List(parentNotFoundExample, notDirectoryExample, circularParentExample))
          )
        )
      )

  val newVersionEndpoint: Endpoint[
    (AuthenticationInputs, CollectionId, FileId, NewVersionReq),
    ErrorResponse,
    FileData,
    ZioStreams with capabilities.WebSockets
  ] =
    filesEndpoint
      .summary("Upload new file version")
      .post
      .prependIn(authRequestParts)
      .in(path[FileId]("fileId") / "versions")
      .in(
        jsonBody[NewVersionReq].example(
          NewVersionReq(
            Some(MimeType("text/plain")),
            exampleFileContent,
            ContentDigest("7d865e959b2466918c9863afca942d0fb89d7c9ac0c99bafc3749504ded97730")
          )
        )
      )
      .out(jsonBody[FileData])
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound]),
          oneOfMapping(
            StatusCode.UnprocessableEntity,
            jsonBody[ErrorResponse.UnprocessableEntity].examples(List(contentNotChangedExample))
          ),
          oneOfMapping(
            StatusCode.PreconditionFailed,
            jsonBody[ErrorResponse.PreconditionFailed].examples(List(encryptionKeyVersionMismatchExample))
          )
        )
      )

  val listVersionsEndpoint
    : Endpoint[(AuthenticationInputs, CollectionId, FileId), ErrorResponse, List[FileData], ZioStreams with capabilities.WebSockets] =
    filesEndpoint
      .summary("Get all versions of file")
      .get
      .prependIn(authRequestParts)
      .in(path[FileId]("fileId") / "versions")
      .out(jsonBody[List[FileData]])
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound]),
          oneOfMapping(
            StatusCode.PreconditionFailed,
            jsonBody[ErrorResponse.PreconditionFailed].examples(List(notAFileExample))
          )
        )
      )

  val deleteFileEndpoint: Endpoint[
    (AuthenticationInputs, CollectionId, FileId, Option[Boolean]),
    ErrorResponse,
    Unit,
    ZioStreams with capabilities.WebSockets
  ] = filesEndpoint
    .summary("Delete file or directory")
    .delete
    .prependIn(authRequestParts)
    .in(path[FileId]("fileId"))
    .in(query[Option[Boolean]]("deleteNonEmpty").description("Allow to delete non empty directories. Default false"))
    .out(statusCode(StatusCode.NoContent))
    .errorOut(
      oneOf[ErrorResponse](
        badRequest,
        unauthorized,
        forbidden,
        oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound]),
        oneOfMapping(StatusCode.PreconditionFailed, jsonBody[ErrorResponse].examples(List(recursivelyDeleteForbiddenExample)))
      )
    )

  val deleteVersionEndpoint
    : Endpoint[(AuthenticationInputs, CollectionId, FileId, FileVersionId), ErrorResponse, Unit, ZioStreams with capabilities.WebSockets] =
    filesEndpoint
      .summary("Delete single version of file")
      .delete
      .prependIn(authRequestParts)
      .in(path[FileId]("fileId") / "versions" / path[FileVersionId]("versionId"))
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound]),
          oneOfMapping(StatusCode.PreconditionFailed, jsonBody[ErrorResponse].examples(List(deleteLatestVersionExample, notAFileExample)))
        )
      )

  val endpoints: NonEmptyList[ZEndpoint[_, _, _]] = NonEmptyList.of(
    createStorageUnitEndpoint,
    getStorageUnitEndpoint,
    getFileContent,
    getParentsEndpoint,
    getRootChildrenEndpoint,
    getChildrenEndpoint,
    updateMetadataEndpoint,
    newVersionEndpoint,
    listVersionsEndpoint,
    deleteFileEndpoint,
    deleteVersionEndpoint
  )

}
