package com.github.huronapp.api.domain.files

import cats.syntax.show._
import com.github.huronapp.api.auth.authorization.{AuthorizationError => AuthError}
import com.github.huronapp.api.domain.collections.CollectionId
import io.chrisdavenport.fuuid.FUUID

sealed trait FileError {

  val logMessage: String

}

sealed trait CreateDirectoryError extends FileError

sealed trait InvalidParentError extends CreateDirectoryError with CreateFileError with UpdateMetadataError with ListChildrenError

sealed trait CreateFileError extends FileError

sealed trait GetMetadataError extends FileError

sealed trait GetContentError extends FileError

sealed trait GetParentsError extends FileError

sealed trait UpdateMetadataError extends FileError

sealed trait CreateVersionError extends FileError

sealed trait ListFileVersionsError extends FileError

sealed trait DeleteFileError extends FileError

sealed trait DeleteVersionError extends FileError

sealed trait ListChildrenError extends FileError

final case class AuthorizationError(error: AuthError)
    extends CreateDirectoryError
    with CreateFileError
    with GetMetadataError
    with GetContentError
    with GetParentsError
    with UpdateMetadataError
    with CreateVersionError
    with ListFileVersionsError
    with DeleteFileError
    with DeleteVersionError
    with ListChildrenError {

  override val logMessage: String = error.message

}

final case class FileAlreadyExists(collectionId: CollectionId, parent: Option[FileId], fileName: String)
    extends CreateDirectoryError
    with CreateFileError
    with UpdateMetadataError {

  override val logMessage: String =
    show"File or directory $fileName already exists in collection $collectionId, ${parent.map(_.id.show).getOrElse[String]("top level")} directory"

}

final case class ParentNotFound(collectionId: CollectionId, parentId: FileId) extends InvalidParentError {

  override val logMessage: String = show"Directory $parentId not found in collection $collectionId"

}

final case class ParentIsNotDirectory(collectionId: CollectionId, parentId: FileId) extends InvalidParentError {

  override val logMessage: String = show"Object $parentId in collection $collectionId is not a directory"

}

final case class EncryptionKeyVersionMismatch(collectionId: CollectionId, providedKey: FUUID, currentKey: FUUID)
    extends CreateFileError
    with CreateVersionError {

  override val logMessage: String = show"Current encryption key for collection $collectionId is $currentKey, but provided $providedKey"

}

final case class FileNotFound(collectionId: CollectionId, fileId: FileId, version: Option[FileVersionId])
    extends GetMetadataError
    with GetContentError
    with GetParentsError
    with UpdateMetadataError
    with CreateVersionError
    with ListFileVersionsError
    with DeleteFileError
    with DeleteVersionError {

  override val logMessage: String =
    show"File or directory $fileId with version ${version.map(_.id.show).getOrElse[String]("latest")} not found in collection $collectionId"

}

final case class NotAFile(collectionId: CollectionId, storageUnitId: FileId)
    extends GetContentError
    with ListFileVersionsError
    with DeleteVersionError {

  override val logMessage: String = show"Object $storageUnitId from collection $collectionId is not a file"

}

final case class NoUpdates[A](collectionId: CollectionId, resourceType: String, resourceId: FUUID, dto: A) extends UpdateMetadataError {

  override val logMessage: String = show"No updates provided for $resourceType $resourceId in collection $collectionId"

}

final case class NewParentSetToSelf(collectionId: CollectionId, objectId: FileId) extends UpdateMetadataError {

  override val logMessage: String = show"New parent for object $objectId in collection $collectionId set to self"

}

final case class CircularParentSet(collectionId: CollectionId, objectId: FileId, newParentId: FileId) extends UpdateMetadataError {

  override val logMessage: String =
    show"Object $objectId from collection $collectionId is going to set parent to $newParentId which already contains this object in parents tree"

}

final case class FileContentNotChanged(collectionId: CollectionId, fileId: FileId) extends CreateVersionError {

  override val logMessage: String = "Content of the new version is equal to previous one"

}

final case class DirectoryToDeleteContainsChildren(collectionId: CollectionId, directoryId: FileId) extends DeleteFileError {

  override val logMessage: String =
    show"Delete directory $directoryId from collection $collectionId impossible, because it contains children"

}

final case class DeleteLatestVersionImpossibleError(collectionId: CollectionId, fileId: FileId, versionId: FileVersionId)
    extends DeleteVersionError {

  override val logMessage: String = show"Attempt to delete $versionId version of the file $fileId which is the latest version of this file"

}
