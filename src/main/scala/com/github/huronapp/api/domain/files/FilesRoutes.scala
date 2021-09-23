package com.github.huronapp.api.domain.files

import cats.syntax.semigroupk._
import com.github.huronapp.api.auth.authentication.HttpAuthentication
import com.github.huronapp.api.auth.authentication.HttpAuthentication.HttpAuthentication
import com.github.huronapp.api.domain.collections.CollectionId
import com.github.huronapp.api.domain.files.FilesService.FilesService
import com.github.huronapp.api.domain.files.dto.fields.{EncryptedBytes, EncryptedContentAlgorithm, Iv}
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
  UpdateStorageUnitMetadataReq,
  VersionData
}
import com.github.huronapp.api.domain.users.UserId
import com.github.huronapp.api.http.{BaseRouter, ErrorResponse}
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import com.github.huronapp.api.http.EndpointSyntax._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Greater
import io.scalaland.chimney.dsl._
import org.http4s.HttpRoutes
import zio.logging.{Logger, Logging}
import zio.{Has, URIO, ZIO, ZLayer}
import zio.interop.catz._

object FilesRoutes {

  type FilesRoutes = Has[FilesRoutes.Service]

  trait Service {

    val routes: HttpRoutes[RouteEffect]

  }

  val routes: URIO[FilesRoutes, HttpRoutes[RouteEffect]] = ZIO.access[FilesRoutes](_.get.routes)

  val live: ZLayer[FilesService with Logging with HttpAuthentication, Nothing, Has[Service]] =
    ZLayer.fromServices[FilesService.Service, Logger[String], HttpAuthentication.Service, FilesRoutes.Service](
      (filesService, logger, auth) =>
        new Service with BaseRouter {

          private val createDirectoryRoute: HttpRoutes[RouteEffect] =
            FilesEndpoints.createStorageUnitEndpoint.toAuthenticatedRoutes[(CollectionId, NewStorageUnitReq)](auth.asUser) {
              case (user, (collectionId, dto: NewDirectory)) =>
                filesService
                  .createDirectoryAs(UserId(user.userId), collectionId, dto)
                  .map(_.transformInto[DirectoryData])
                  .flatMapError(error => logger.warn(error.logMessage).as(FileErrorMapping.createDirectoryError(error)))

              case (user, (collectionId, dto: NewFile))      =>
                filesService
                  .createFileAs(UserId(user.userId), collectionId, dto)
                  .map(file => file.transformInto[FileData])
                  .flatMapError(error => logger.warn(error.logMessage).as(FileErrorMapping.createFileError(error)))
            }

          private val getMetadataRoute: HttpRoutes[RouteEffect] =
            FilesEndpoints.getStorageUnitEndpoint.toAuthenticatedRoutes[(CollectionId, FileId, Option[FileVersionId])](auth.asUser) {
              case (user, (collectionId, fileId, maybeVersionId)) =>
                filesService
                  .getStorageUnitMetadataAs(
                    UserId(user.userId),
                    collectionId,
                    fileId,
                    maybeVersionId
                  )
                  .map(StorageUnitData.fromDomain)
                  .flatMapError(error => logger.warn(error.logMessage).as(FileErrorMapping.readMetadataError(error)))
            }

          private val getContentRoute: HttpRoutes[RouteEffect] =
            FilesEndpoints.getFileContent.toAuthenticatedRoutes[(CollectionId, FileId, Option[FileVersionId])](auth.asUser) {
              case (user, (collectionId, fileId, maybeVersionId)) =>
                filesService
                  .getFileContentAs(UserId(user.userId), collectionId, fileId, maybeVersionId)
                  .map {
                    case (content, metadata) =>
                      FileContentData(
                        EncryptedContent(
                          EncryptedContentAlgorithm(metadata.encryptionParams.algorithm),
                          Iv(metadata.encryptionParams.iv),
                          metadata.encryptionParams.encryptionKeyVersion,
                          EncryptedBytes(content)
                        ),
                        metadata.originalDigest,
                        metadata.name,
                        metadata.mimeType
                      )
                  }
                  .flatMapError(error => logger.warn(error.logMessage).as(FileErrorMapping.readContentError(error)))
            }

          private val getParentsRoute: HttpRoutes[RouteEffect] =
            FilesEndpoints
              .getParentsEndpoint
              .toAuthenticatedRoutes[(CollectionId, FileId, Option[Int Refined Greater[0]])](auth.asUser) {
                case (user, (collectionId, storageUnitId, maybeDepth)) =>
                  filesService
                    .getParentsAs(UserId(user.userId), collectionId, storageUnitId, maybeDepth.map(_.value))
                    .map(_.transformInto[List[DirectoryData]])
                    .flatMapError(error => logger.warn(error.logMessage).as(FileErrorMapping.readParentsError(error)))
              }

          private val updateMetadataRoute: HttpRoutes[RouteEffect] =
            FilesEndpoints.updateMetadataEndpoint.toAuthenticatedRoutes[(CollectionId, FileId, UpdateStorageUnitMetadataReq)](auth.asUser) {
              case (user, (collectionId, fileId, dto)) =>
                filesService
                  .updateMetadataAs(UserId(user.userId), collectionId, fileId, dto)
                  .map(StorageUnitData.fromDomain)
                  .flatMapError(error => logger.warn(error.logMessage).as(FileErrorMapping.updateMetadataError(error)))
            }

          private val newVersionRoute: HttpRoutes[RouteEffect] =
            FilesEndpoints.newVersionEndpoint.toAuthenticatedRoutes[(CollectionId, FileId, NewVersionReq)](auth.asUser) {
              case (user, (collectionId, fileId, dto)) =>
                filesService
                  .addFileVersionAs(UserId(user.userId), collectionId, fileId, dto)
                  .map(_.transformInto[FileData])
                  .flatMapError(error => logger.warn(error.logMessage).as(FileErrorMapping.createVersionError(error)))
            }

          private val listVersionsRoute: HttpRoutes[RouteEffect] =
            FilesEndpoints.listVersionsEndpoint.toAuthenticatedRoutes[(CollectionId, FileId)](auth.asUser) {
              case (user, (collectionId, fileId)) =>
                filesService
                  .listVersionsAs(UserId(user.userId), collectionId, fileId)
                  .map(_.transformInto[List[VersionData]])
                  .flatMapError(error => logger.warn(error.logMessage).as(FileErrorMapping.listFileVersionsError(error)))
            }

          private val deleteFileRoute: HttpRoutes[RouteEffect] =
            FilesEndpoints.deleteFileEndpoint.toAuthenticatedRoutes[(CollectionId, FileId, Option[Boolean])](auth.asUser) {
              case (user, (collectionId, fileId, deleteNonEmpty)) =>
                filesService
                  .deleteFileOrDirectoryAs(UserId(user.userId), collectionId, fileId, deleteNonEmpty.getOrElse(false))
                  .flatMapError(error => logger.warn(error.logMessage).as(FileErrorMapping.deleteFileError(error)))
            }

          private val deleteVersionRoute: HttpRoutes[RouteEffect] =
            FilesEndpoints.deleteVersionEndpoint.toAuthenticatedRoutes[(CollectionId, FileId, FileVersionId)](auth.asUser) {
              case (user, (collectionId, fileId, versionId)) =>
                filesService
                  .deleteVersionAs(UserId(user.userId), collectionId, fileId, versionId)
                  .flatMapError(error => logger.warn(error.logMessage).as(FileErrorMapping.deleteVersionError(error)))
            }

          private val listRootDirChildren: HttpRoutes[RouteEffect] =
            FilesEndpoints.getRootChildrenEndpoint.toAuthenticatedRoutes[CollectionId](auth.asUser) {
              case (user, collectionId) => paramsToListChildren(UserId(user.userId), collectionId, None)
            }

          private val listDirChildren: HttpRoutes[RouteEffect] =
            FilesEndpoints.getChildrenEndpoint.toAuthenticatedRoutes[(CollectionId, FileId)](auth.asUser) {
              case (user, (collectionId, dirId)) => paramsToListChildren(UserId(user.userId), collectionId, Some(dirId))
            }

          private def paramsToListChildren(
            userId: UserId,
            collectionId: CollectionId,
            maybeDirId: Option[FileId]
          ): ZIO[Any, ErrorResponse, List[StorageUnitData]] =
            filesService
              .getChildrenAs(userId, collectionId, maybeDirId)
              .map(files => files.map(StorageUnitData.fromDomain))
              .flatMapError(error => logger.warn(error.logMessage).as(FileErrorMapping.listChildrenError(error)))

          override val routes: HttpRoutes[RouteEffect] = createDirectoryRoute <+>
            getMetadataRoute <+>
            getContentRoute <+>
            getParentsRoute <+>
            listRootDirChildren <+>
            listDirChildren <+>
            updateMetadataRoute <+>
            newVersionRoute <+>
            listVersionsRoute <+>
            deleteFileRoute <+> deleteVersionRoute

        }
    )

}
