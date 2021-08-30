package com.github.huronapp.api.domain.files

import cats.syntax.show._
import com.github.huronapp.api.http.ErrorResponse

object FileErrorMapping {

  def createDirectoryError(error: CreateDirectoryError): ErrorResponse =
    error match {
      case _: AuthorizationError                        => ErrorResponse.Forbidden("Operation not permitted")
      case FileAlreadyExists(_, _, fileName)            => ErrorResponse.Conflict(show"File or directory $fileName already exists")
      case ParentNotFound(collectionId, parentId)       => FilesEndpoints.Responses.parentNotFound(collectionId, parentId)
      case ParentIsNotDirectory(collectionId, parentId) => FilesEndpoints.Responses.notDirectory(collectionId, parentId)
    }

  def createFileError(error: CreateFileError): ErrorResponse =
    error match {
      case _: AuthorizationError                                               => ErrorResponse.Forbidden("Operation not permitted")
      case FileAlreadyExists(_, _, fileName)                                   => ErrorResponse.Conflict(show"File or directory $fileName already exists")
      case ParentNotFound(collectionId, parentId)                              => FilesEndpoints.Responses.parentNotFound(collectionId, parentId)
      case ParentIsNotDirectory(collectionId, parentId)                        => FilesEndpoints.Responses.notDirectory(collectionId, parentId)
      case EncryptionKeyVersionMismatch(collectionId, providedKey, currentKey) =>
        FilesEndpoints.Responses.encryptionKeyVersionMismatch(collectionId, providedKey, currentKey)
    }

  def readMetadataError(error: GetMetadataError): ErrorResponse =
    error match {
      case _: AuthorizationError                            => ErrorResponse.Forbidden("Operation not permitted")
      case FileNotFound(collectionId, fileId, maybeVersion) =>
        ErrorResponse.NotFound(
          show"File or directory $fileId with version ${maybeVersion.map(_.id.show).getOrElse[String]("latest")} not found in collection $collectionId"
        )
    }

  def readContentError(error: GetContentError): ErrorResponse =
    error match {
      case _: AuthorizationError                 => ErrorResponse.Forbidden("Operation not permitted")
      case _: FileNotFound                       => ErrorResponse.NotFound("File or version not found")
      case NotAFile(collectionId, storageUnitId) => FilesEndpoints.Responses.objectIsNotAFile(collectionId, storageUnitId)
    }

  def readParentsError(error: GetParentsError): ErrorResponse =
    error match {
      case _: AuthorizationError => ErrorResponse.Forbidden("Operation not permitted")
      case _: FileNotFound       => ErrorResponse.NotFound("File not found")
    }

  def updateMetadataError(error: UpdateMetadataError): ErrorResponse =
    error match {
      case ParentIsNotDirectory(collectionId, parentId)           => FilesEndpoints.Responses.notDirectory(collectionId, parentId)
      case ParentNotFound(collectionId, parentId)                 => FilesEndpoints.Responses.parentNotFound(collectionId, parentId)
      case _: AuthorizationError                                  => ErrorResponse.Forbidden("Operation not permitted")
      case FileAlreadyExists(_, _, fileName)                      => ErrorResponse.Conflict(show"File or directory $fileName already exists")
      case _: FileNotFound                                        => ErrorResponse.NotFound("File or directory not found")
      case _: NoUpdates[_]                                        => ErrorResponse.BadRequest("No updates in request")
      case _: NewParentSetToSelf                                  => ErrorResponse.BadRequest("New parent set to self")
      case CircularParentSet(collectionId, objectId, newParentId) =>
        FilesEndpoints.Responses.circularParent(collectionId, objectId, newParentId)
    }

  def createVersionError(error: CreateVersionError): ErrorResponse =
    error match {
      case _: AuthorizationError                                               => ErrorResponse.Forbidden("Operation not permitted")
      case _: FileNotFound                                                     => ErrorResponse.NotFound("File not found")
      case _: FileContentNotChanged                                            => FilesEndpoints.Responses.fileContentNotChanged
      case EncryptionKeyVersionMismatch(collectionId, providedKey, currentKey) =>
        FilesEndpoints.Responses.encryptionKeyVersionMismatch(collectionId, providedKey, currentKey)
    }

  def listFileVersionsError(error: ListFileVersionsError): ErrorResponse =
    error match {
      case _: AuthorizationError                 => ErrorResponse.Forbidden("Operation not permitted")
      case _: FileNotFound                       => ErrorResponse.NotFound("File not found")
      case NotAFile(collectionId, storageUnitId) => FilesEndpoints.Responses.objectIsNotAFile(collectionId, storageUnitId)
    }

  def deleteFileError(error: DeleteFileError): ErrorResponse =
    error match {
      case _: AuthorizationError                => ErrorResponse.Forbidden("Operation not permitted")
      case _: FileNotFound                      => ErrorResponse.NotFound("File or directory not found")
      case _: DirectoryToDeleteContainsChildren => FilesEndpoints.Responses.recursivelyDeleteForbidden
    }

  def deleteVersionError(error: DeleteVersionError): ErrorResponse =
    error match {
      case _: AuthorizationError                 => ErrorResponse.Forbidden("Operation not permitted")
      case _: FileNotFound                       => ErrorResponse.NotFound("File or version not found")
      case NotAFile(collectionId, storageUnitId) => FilesEndpoints.Responses.objectIsNotAFile(collectionId, storageUnitId)
      case _: DeleteLatestVersionImpossibleError => FilesEndpoints.Responses.deleteLatestVersion
    }

  def listChildrenError(error: ListChildrenError): ErrorResponse =
    error match {
      case _: AuthorizationError                        => ErrorResponse.Forbidden("Operation not permitted")
      case ParentIsNotDirectory(collectionId, parentId) => FilesEndpoints.Responses.notDirectory(collectionId, parentId)
      case _: ParentNotFound                            => ErrorResponse.NotFound("Directory not found")
    }

}
