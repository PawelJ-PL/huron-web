package com.github.huronapp.api.testdoubles

import com.github.huronapp.api.constants.Files
import com.github.huronapp.api.domain.files.FilesService.FilesService
import com.github.huronapp.api.domain.files.dto.{NewDirectory, NewFile, NewVersionReq, UpdateStorageUnitMetadataReq}
import com.github.huronapp.api.domain.files.{
  CreateDirectoryError,
  CreateFileError,
  CreateVersionError,
  DeleteFileError,
  DeleteVersionError,
  Directory,
  File,
  FilesService,
  GetContentError,
  GetMetadataError,
  GetParentsError,
  ListChildrenError,
  ListFileVersionsError,
  StorageUnit,
  UpdateMetadataError
}
import com.github.huronapp.api.domain.{collections, files, users}
import zio.{ULayer, ZIO, ZLayer}

object FilesServiceStub extends Files {

  final case class FilesServiceResponses(
    createNewDirectory: ZIO[Any, CreateDirectoryError, Directory] = ZIO.succeed(ExampleDirectoryMetadata),
    createNewFile: ZIO[Any, CreateFileError, File] = ZIO.succeed(ExampleFileMetadata),
    getUnitMetadata: ZIO[Any, GetMetadataError, StorageUnit] = ZIO.succeed(ExampleFileMetadata),
    getFileContent: ZIO[Any, GetContentError, (Array[Byte], File)] = ZIO.succeed((ExampleFileContent, ExampleFileMetadata)),
    listParents: ZIO[Any, GetParentsError, List[Directory]] = ZIO.succeed(List(ExampleDirectoryMetadata)),
    updateMetadata: ZIO[Any, UpdateMetadataError, StorageUnit] = ZIO.succeed(ExampleFileMetadata),
    addVersion: ZIO[Any, CreateVersionError, File] = ZIO.succeed(ExampleFileMetadata),
    listVersions: ZIO[Any, ListFileVersionsError, List[File]] = ZIO.succeed(List(ExampleFileMetadata)),
    deleteFile: ZIO[Any, DeleteFileError, Unit] = ZIO.unit,
    deleteVersion: ZIO[Any, DeleteVersionError, Unit] = ZIO.unit,
    listChildren: ZIO[Any, ListChildrenError, List[StorageUnit]] = ZIO.succeed(List(ExampleFileMetadata)))

  def withResponses(responses: FilesServiceResponses): ULayer[FilesService] =
    ZLayer.succeed(new FilesService.Service {

      override def createDirectoryAs(
        userId: users.UserId,
        collectionId: collections.CollectionId,
        dto: NewDirectory
      ): ZIO[Any, CreateDirectoryError, Directory] = responses.createNewDirectory

      override def createFileAs(
        userId: users.UserId,
        collectionId: collections.CollectionId,
        dto: NewFile
      ): ZIO[Any, CreateFileError, File] = responses.createNewFile

      override def getStorageUnitMetadataAs(
        userId: users.UserId,
        collectionId: collections.CollectionId,
        unitId: files.FileId,
        version: Option[files.FileVersionId]
      ): ZIO[Any, GetMetadataError, StorageUnit] = responses.getUnitMetadata

      override def getFileContentAs(
        userId: users.UserId,
        collectionId: collections.CollectionId,
        fileId: files.FileId,
        version: Option[files.FileVersionId]
      ): ZIO[Any, GetContentError, (Array[Byte], File)] = responses.getFileContent

      override def getParentsAs(
        userId: users.UserId,
        collectionId: collections.CollectionId,
        storageUnitId: files.FileId,
        limit: Option[Int]
      ): ZIO[Any, GetParentsError, List[Directory]] = responses.listParents

      override def getChildrenAs(
        userId: users.UserId,
        collectionId: collections.CollectionId,
        directoryId: Option[files.FileId]
      ): ZIO[Any, ListChildrenError, List[StorageUnit]] = responses.listChildren

      override def updateMetadataAs(
        userId: users.UserId,
        collectionId: collections.CollectionId,
        storageUnitId: files.FileId,
        dto: UpdateStorageUnitMetadataReq
      ): ZIO[Any, UpdateMetadataError, StorageUnit] = responses.updateMetadata

      override def addFileVersionAs(
        userId: users.UserId,
        collectionId: collections.CollectionId,
        fileId: files.FileId,
        dto: NewVersionReq
      ): ZIO[Any, CreateVersionError, File] = responses.addVersion

      override def listVersionsAs(
        userId: users.UserId,
        collectionId: collections.CollectionId,
        fileId: files.FileId
      ): ZIO[Any, ListFileVersionsError, List[File]] = responses.listVersions

      override def deleteFileOrDirectoryAs(
        userId: users.UserId,
        collectionId: collections.CollectionId,
        fileId: files.FileId,
        deleteNonEmptyDirs: Boolean
      ): ZIO[Any, DeleteFileError, Unit] = responses.deleteFile

      override def deleteVersionAs(
        userId: users.UserId,
        collectionId: collections.CollectionId,
        fileId: files.FileId,
        versionId: files.FileVersionId
      ): ZIO[Any, DeleteVersionError, Unit] = responses.deleteVersion

    })

}
