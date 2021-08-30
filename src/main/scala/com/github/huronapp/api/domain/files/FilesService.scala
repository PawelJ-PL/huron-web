package com.github.huronapp.api.domain.files

import cats.syntax.eq._
import cats.syntax.show._
import com.github.huronapp.api.auth.authorization.{AuthorizationKernel, CreateFile, DeleteFile, ModifyFile, ReadContent, ReadMetadata}
import com.github.huronapp.api.auth.authorization.AuthorizationKernel.AuthorizationKernel
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.domain.collections.{CollectionId, CollectionsRepository}
import com.github.huronapp.api.domain.collections.CollectionsRepository.CollectionsRepository
import com.github.huronapp.api.domain.files.FilesMetadataRepository.FilesMetadataRepository
import com.github.huronapp.api.domain.files.dto.{NewDirectory, NewFile, NewVersionReq, UpdateStorageUnitMetadataReq}
import com.github.huronapp.api.domain.users.UserId
import com.github.huronapp.api.utils.FileSystemService.FileSystemService
import com.github.huronapp.api.utils.{FileSystemService, RandomUtils}
import com.github.huronapp.api.utils.RandomUtils.RandomUtils
import com.github.huronapp.api.utils.crypto.Crypto
import com.github.huronapp.api.utils.crypto.Crypto.Crypto
import com.github.huronapp.api.utils.crypto.DigestAlgo.Sha256
import com.github.huronapp.api.utils.outbox.{OutboxCommand, OutboxService}
import com.github.huronapp.api.utils.outbox.OutboxService.OutboxService
import doobie.util.transactor
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.doobie.{Connection, Database}
import zio.logging.{Logger, Logging}
import zio.{Has, Task, ZIO, ZLayer}
import zio.macros.accessible

import java.io.FileNotFoundException
import java.time.Instant

@accessible
object FilesService {

  type FilesService = Has[FilesService.Service]

  trait Service {

    def createDirectoryAs(userId: UserId, collectionId: CollectionId, dto: NewDirectory): ZIO[Any, CreateDirectoryError, Directory]

    def createFileAs(userId: UserId, collectionId: CollectionId, dto: NewFile): ZIO[Any, CreateFileError, File]

    def getStorageUnitMetadataAs(
      userId: UserId,
      collectionId: CollectionId,
      unitId: FileId,
      version: Option[FileVersionId]
    ): ZIO[Any, GetMetadataError, StorageUnit]

    def getFileContentAs(
      userId: UserId,
      collectionId: CollectionId,
      fileId: FileId,
      version: Option[FileVersionId]
    ): ZIO[Any, GetContentError, (Array[Byte], File)]

    def getParentsAs(
      userId: UserId,
      collectionId: CollectionId,
      storageUnitId: FileId,
      limit: Option[Int]
    ): ZIO[Any, GetParentsError, List[Directory]]

    def getChildrenAs(
      userId: UserId,
      collectionId: CollectionId,
      directoryId: Option[FileId]
    ): ZIO[Any, ListChildrenError, List[StorageUnit]]

    def updateMetadataAs(
      userId: UserId,
      collectionId: CollectionId,
      storageUnitId: FileId,
      dto: UpdateStorageUnitMetadataReq
    ): ZIO[Any, UpdateMetadataError, StorageUnit]

    def addFileVersionAs(userId: UserId, collectionId: CollectionId, fileId: FileId, dto: NewVersionReq): ZIO[Any, CreateVersionError, File]

    def listVersionsAs(userId: UserId, collectionId: CollectionId, fileId: FileId): ZIO[Any, ListFileVersionsError, List[File]]

    def deleteFileOrDirectoryAs(
      userId: UserId,
      collectionId: CollectionId,
      fileId: FileId,
      deleteNonEmptyDirs: Boolean
    ): ZIO[Any, DeleteFileError, Unit]

    def deleteVersionAs(
      userId: UserId,
      collectionId: CollectionId,
      fileId: FileId,
      versionId: FileVersionId
    ): ZIO[Any, DeleteVersionError, Unit]

  }

  val live: ZLayer[
    Database.Database with FilesMetadataRepository with AuthorizationKernel with RandomUtils with Logging with Crypto with FileSystemService with CollectionsRepository with OutboxService,
    Nothing,
    FilesService
  ] =
    ZLayer.fromServices[Database.Service, FilesMetadataRepository.Service, AuthorizationKernel.Service, RandomUtils.Service, Logger[
      String
    ], Crypto.Service, FileSystemService.Service, CollectionsRepository.Service, OutboxService.Service, FilesService.Service](
      (db, filesRepo, authKernel, random, logger, crypto, fs, collectionsRepo, outbox) =>
        new Service {

          override def createDirectoryAs(
            userId: UserId,
            collectionId: CollectionId,
            dto: NewDirectory
          ): ZIO[Any, CreateDirectoryError, Directory] =
            db.transactionOrDie(
              for {
                _           <- authKernel.authorizeOperation(CreateFile(Subject(userId.id), collectionId)).mapError(AuthorizationError)
                maybeParent = dto.parent.map(p => FileId(p))
                _           <- filesRepo
                                 .getFileByNameIn(collectionId, maybeParent, dto.name.value)
                                 .orDie
                                 .none
                                 .orElseFail(FileAlreadyExists(collectionId, maybeParent, dto.name.value))
                _           <- ZIO.foreach_(maybeParent)(p => verifyParent(p, collectionId))
                directoryId <- random.randomFuuid
                directory = Directory(FileId(directoryId), collectionId, maybeParent, dto.name.value, Instant.EPOCH)
                created     <- filesRepo.createDirectory(directory).orDie
                _           <- logger.info(show"User $userId has just created directory $directoryId in collection $collectionId ")
              } yield created
            )

          private def verifyParent(parentId: FileId, collectionId: CollectionId): ZIO[Connection, InvalidParentError, Unit] =
            for {
              fileOrDir <- filesRepo.getFile(collectionId, parentId, None).orDie.someOrFail(ParentNotFound(collectionId, parentId))
              _         <- fileOrDir match {
                             case _: Directory => ZIO.unit
                             case _            => ZIO.fail(ParentIsNotDirectory(collectionId, parentId))
                           }
            } yield ()

          override def createFileAs(userId: UserId, collectionId: CollectionId, dto: NewFile): ZIO[Any, CreateFileError, File] =
            db.transactionOrDie(
              for {
                _             <- authKernel.authorizeOperation(CreateFile(Subject(userId.id), collectionId)).mapError(AuthorizationError)
                maybeParent = dto.parent.map(p => FileId(p))
                _             <- filesRepo
                                   .getFileByNameIn(collectionId, maybeParent, dto.name.value)
                                   .orDie
                                   .none
                                   .orElseFail(FileAlreadyExists(collectionId, maybeParent, dto.name.value))
                _             <- ZIO.foreach_(maybeParent)(p => verifyParent(p, collectionId))
                _             <- verifyEncryptionKeyVersion(collectionId, dto.content.encryptionKeyVersion)
                fileId        <- random.randomFuuid
                versionId     <- random.randomFuuid
                contentDigest <- crypto.digest(dto.content.bytes.value, Sha256)
                file = File(
                         FileId(fileId),
                         collectionId,
                         maybeParent,
                         dto.name.value,
                         FileVersionId(versionId),
                         Some(userId),
                         dto.content.bytes.value.length.toLong,
                         dto.mimeType.map(_.value),
                         dto.contentDigest.value,
                         contentDigest,
                         EncryptionParams(dto.content.algorithm.value, dto.content.iv.value, dto.content.encryptionKeyVersion),
                         Instant.EPOCH,
                         Instant.EPOCH
                       )
                created       <- filesRepo.createFile(file).orDie
                _             <- fs.saveFile(filePath(collectionId, FileId(fileId), FileVersionId(versionId)), dto.content.bytes.value).orDie
                _             <- logger.info(show"User $userId created a new file ${FileId(fileId)} in collection $collectionId")
              } yield created
            )

          private def verifyEncryptionKeyVersion(
            collectionId: CollectionId,
            encryptionKeyVersion: FUUID
          ): ZIO[Connection, EncryptionKeyVersionMismatch, Unit] =
            for {
              collection <- collectionsRepo
                              .getCollectionDetails(collectionId.id)
                              .someOrFail(new RuntimeException(show"Collection $collectionId not found"))
                              .orDie
              _          <- ZIO.cond(
                              collection.encryptionKeyVersion === encryptionKeyVersion,
                              (),
                              EncryptionKeyVersionMismatch(collectionId, encryptionKeyVersion, collection.encryptionKeyVersion)
                            )
            } yield ()

          private def fileDirectoryPath(collectionId: CollectionId, fileId: FileId) =
            show"user_files/$collectionId/${fileId.id.show.substring(0, 2)}/$fileId"

          private def filePath(collectionId: CollectionId, fileId: FileId, versionId: FileVersionId) =
            fileDirectoryPath(collectionId, fileId) + show"/$versionId"

          override def getStorageUnitMetadataAs(
            userId: UserId,
            collectionId: CollectionId,
            unitId: FileId,
            version: Option[FileVersionId]
          ): ZIO[Any, GetMetadataError, StorageUnit] =
            db.transactionOrDie(
              for {
                _      <- authKernel.authorizeOperation(ReadMetadata(Subject(userId.id), collectionId)).mapError(AuthorizationError)
                result <- filesRepo.getFile(collectionId, unitId, version).orDie.someOrFail(FileNotFound(collectionId, unitId, version))
              } yield result
            )

          override def getFileContentAs(
            userId: UserId,
            collectionId: CollectionId,
            fileId: FileId,
            version: Option[FileVersionId]
          ): ZIO[Any, GetContentError, (Array[Byte], File)] =
            db.transactionOrDie(
              for {
                _             <- authKernel.authorizeOperation(ReadContent(Subject(userId.id), collectionId)).mapError(AuthorizationError)
                fileOrDir     <- filesRepo.getFile(collectionId, fileId, version).orDie.someOrFail(FileNotFound(collectionId, fileId, version))
                file          <- fileOrDir match {
                                   case f: File      => ZIO.succeed(f)
                                   case _: Directory => ZIO.fail(NotAFile(collectionId, fileId))
                                 }
                content       <- fs.readFile(filePath(collectionId, fileId, file.versionId))
                                   .someOrFail(new FileNotFoundException(show"file ${filePath(collectionId, fileId, file.versionId)} not found"))
                                   .orDie
                contentDigest <- crypto.digest(content, Sha256)
                _             <- ZIO
                                   .cond(
                                     contentDigest === file.encryptedDigest,
                                     (),
                                     new RuntimeException(
                                       show"File ${filePath(collectionId, fileId, file.versionId)} digest mismatch. Expected ${file.encryptedDigest}, found $contentDigest"
                                     )
                                   )
                                   .orDie
              } yield (content, file)
            )

          override def getParentsAs(
            userId: UserId,
            collectionId: CollectionId,
            storageUnitId: FileId,
            limit: Option[Int]
          ): ZIO[Any, GetParentsError, List[Directory]] =
            db.transactionOrDie(
              for {
                _             <- authKernel.authorizeOperation(ReadMetadata(Subject(userId.id), collectionId)).mapError(AuthorizationError)
                storageObject <-
                  filesRepo.getFile(collectionId, storageUnitId, None).orDie.someOrFail(FileNotFound(collectionId, storageUnitId, None))
                parents       <- storageObject
                                   .parentId
                                   .map(parent => recursivelyReadParents(collectionId, parent, List.empty, limit))
                                   .getOrElse[ZIO[Connection, GetParentsError, List[Directory]]](ZIO.succeed(List.empty))
              } yield parents
            )

          private def recursivelyReadParents(
            collectionId: CollectionId,
            fileId: FileId,
            accumulated: List[Directory],
            limit: Option[Int]
          ): ZIO[Connection, FileNotFound, List[Directory]] =
            if (limit.forall(_ > 0))
              filesRepo.getFile(collectionId, fileId, None).orDie.someOrFail(FileNotFound(collectionId, fileId, None)).flatMap {
                case file: File                                           =>
                  ZIO.die(
                    new RuntimeException(
                      show"Expected object ${file.id} from collection ${file.collectionId} to be directory, but file was found "
                    )
                  )
                case dir @ Directory(_, collection, Some(parentId), _, _) =>
                  recursivelyReadParents(collection, parentId, accumulated.appended(dir), limit.map(_ - 1))
                case dir: Directory                                       => ZIO.succeed(accumulated.appended(dir))
              }
            else
              ZIO.succeed(accumulated)

          override def getChildrenAs(
            userId: UserId,
            collectionId: CollectionId,
            directoryId: Option[FileId]
          ): ZIO[Any, ListChildrenError, List[StorageUnit]] =
            db.transactionOrDie(
              for {
                _        <- authKernel.authorizeOperation(ReadMetadata(Subject(userId.id), collectionId)).mapError(AuthorizationError)
                _        <- directoryId.map(parent => verifyParent(parent, collectionId)).getOrElse(ZIO.unit)
                children <- filesRepo.listChildren(collectionId, directoryId).orDie
              } yield children
            )

          override def updateMetadataAs(
            userId: UserId,
            collectionId: CollectionId,
            storageUnitId: FileId,
            dto: UpdateStorageUnitMetadataReq
          ): ZIO[Any, UpdateMetadataError, StorageUnit] =
            db.transactionOrDie(
              for {
                _           <-
                  ZIO.cond(dto.name.isDefined || dto.parent.isDefined, (), NoUpdates(collectionId, "file metadata", storageUnitId.id, dto))
                _           <- ZIO.cond(
                                 dto.parent.flatMap(_.value).forall(newParent => newParent =!= storageUnitId.id),
                                 (),
                                 NewParentSetToSelf(collectionId, storageUnitId)
                               )
                _           <- authKernel.authorizeOperation(ModifyFile(Subject(userId.id), collectionId)).mapError(AuthorizationError)
                currentFile <-
                  filesRepo.getFile(collectionId, storageUnitId, None).orDie.someOrFail(FileNotFound(collectionId, storageUnitId, None))
                _           <- ZIO.foreach_(dto.parent.flatMap(_.value))(newParentId => verifyParent(FileId(newParentId), collectionId))
                targetDir = dto.parent.map(_.value.map(FileId(_))).getOrElse(currentFile.parentId)
                targetName = dto.name.map(_.value).getOrElse(currentFile.name)
                _           <- verifyFileNameConflict(collectionId, targetDir, targetName).when(dto.name.isDefined || dto.parent.isDefined)
                _           <- dto.parent.flatMap(_.value) match {
                                 case Some(newParent) => verifyCircularParent(collectionId, storageUnitId, FileId(newParent))
                                 case None            => ZIO.unit
                               }
                _           <- filesRepo
                                 .updateFileMetadata(collectionId, storageUnitId, dto.parent.map(_.value.map(FileId(_))), dto.name.map(_.value))
                                 .orDie
                updated     <-
                  filesRepo.getFile(collectionId, storageUnitId, None).orDie.someOrFail(FileNotFound(collectionId, storageUnitId, None))
                _           <- logger.info(s"User $userId updated metadata of object $storageUnitId in collection $collectionId")
              } yield updated
            )

          private def verifyFileNameConflict(
            collectionId: CollectionId,
            parent: Option[FileId],
            fileName: String
          ): ZIO[Connection, FileAlreadyExists, Unit] =
            filesRepo
              .getFileByNameIn(collectionId, parent, fileName)
              .orDie
              .none
              .orElseFail(FileAlreadyExists(collectionId, parent, fileName))

          private def verifyCircularParent(
            collectionId: CollectionId,
            storageUnitId: FileId,
            newParentId: FileId
          ): ZIO[Connection, UpdateMetadataError, Unit] =
            for {
              allParentsOfNewParents <- recursivelyReadParents(collectionId, newParentId, List.empty, None)
              parentIds = allParentsOfNewParents.map(_.parentId).collect {
                            case Some(parent) => parent
                          }
              _                      <- ZIO.cond(!parentIds.contains(storageUnitId), (), CircularParentSet(collectionId, storageUnitId, newParentId))

            } yield ()

          override def addFileVersionAs(
            userId: UserId,
            collectionId: CollectionId,
            fileId: FileId,
            dto: NewVersionReq
          ): ZIO[Any, CreateVersionError, File] =
            db.transactionOrDie(
              for {
                _              <- authKernel.authorizeOperation(ModifyFile(Subject(userId.id), collectionId)).mapError(AuthorizationError)
                currentVersion <- filesRepo
                                    .getFile(collectionId, fileId, None)
                                    .orDie
                                    .map(_.collect {
                                      case f: File => f
                                    })
                                    .someOrFail(FileNotFound(collectionId, fileId, None))
                newDigest      <- crypto.digest(dto.content.bytes.value, Sha256)
                _              <- ZIO.cond(newDigest =!= currentVersion.encryptedDigest, (), FileContentNotChanged(collectionId, fileId))
                _              <- verifyEncryptionKeyVersion(collectionId, dto.content.encryptionKeyVersion)
                newVersionId   <- random.randomFuuid
                file = File(
                         fileId,
                         collectionId,
                         currentVersion.parentId,
                         currentVersion.name,
                         FileVersionId(newVersionId),
                         Some(userId),
                         dto.content.bytes.value.length.toLong,
                         dto.mimeType.map(_.value),
                         dto.contentDigest.value,
                         newDigest,
                         EncryptionParams(dto.content.algorithm.value, dto.content.iv.value, dto.content.encryptionKeyVersion),
                         currentVersion.createdAt,
                         Instant.EPOCH
                       )
                saved          <- filesRepo.addLatestVersion(file).orDie
                _              <- fs.saveFile(filePath(collectionId, fileId, FileVersionId(newVersionId)), dto.content.bytes.value).orDie
                _              <- logger.info(show"User $userId uploaded a new version $newVersionId of file $fileId from collection $collectionId")
              } yield saved
            )

          override def listVersionsAs(
            userId: UserId,
            collectionId: CollectionId,
            fileId: FileId
          ): ZIO[Any, ListFileVersionsError, List[File]] =
            db.transactionOrDie(
              for {
                _        <- authKernel.authorizeOperation(ReadMetadata(Subject(userId.id), collectionId)).mapError(AuthorizationError)
                file     <- filesRepo.getFile(collectionId, fileId, None).orDie.someOrFail(FileNotFound(collectionId, fileId, None))
                _        <- file match {
                              case _: File      => ZIO.unit
                              case _: Directory => ZIO.fail(NotAFile(collectionId, fileId))
                            }
                versions <- filesRepo.getAllVersions(collectionId, fileId).orDie
              } yield versions
            )

          override def deleteFileOrDirectoryAs(
            userId: UserId,
            collectionId: CollectionId,
            fileId: FileId,
            deleteNonEmptyDirs: Boolean
          ): ZIO[Any, DeleteFileError, Unit] =
            db.transactionOrDie(
              for {
                _         <- logger.info(show"User $userId is going to delete object $fileId from collection $collectionId.")
                _         <- authKernel.authorizeOperation(DeleteFile(Subject(userId.id), collectionId)).mapError(AuthorizationError)
                fileOrDir <- filesRepo.getFile(collectionId, fileId, None).orDie.someOrFail(FileNotFound(collectionId, fileId, None))
                _         <- verifyDeleteChildren(fileOrDir, deleteNonEmptyDirs)
                childIds  <- fileOrDir match {
                               case _: File      => ZIO.succeed(List(fileId))
                               case _: Directory => recursivelyGetChildFiles(collectionId, fileId, List.empty)
                             }
                _         <- filesRepo.deleteFile(fileId).orDie
                paths = childIds.map(fileDirectoryPath(collectionId, _))
                _         <- logger.info(show"Following files will be deleted soon: ${paths.mkString(", ")}")
                outboxCommand = OutboxCommand.DeleteFiles(paths, recursively = true)
                commandId <- outbox.saveCommand(outboxCommand).orDie
                _         <- logger.info(show"User $userId deleted object $fileId with children from collection $collectionId")
                _         <- logger.info(show"Saved command $commandId to delete remaining files")
              } yield ()
            )

          private def verifyDeleteChildren(
            storageUnit: StorageUnit,
            deleteWithChildren: Boolean
          ): ZIO[Connection, DirectoryToDeleteContainsChildren, Unit] =
            storageUnit match {
              case _ if deleteWithChildren              => ZIO.unit
              case _: File                              => ZIO.unit
              case Directory(id, collectionId, _, _, _) =>
                for {
                  children <- filesRepo.listChildren(collectionId, Some(id)).orDie
                  _        <- ZIO.cond(children.isEmpty, (), DirectoryToDeleteContainsChildren(collectionId, id))
                } yield ()
            }

          private def recursivelyGetChildFiles(
            collectionId: CollectionId,
            fileId: FileId,
            collected: List[FileId]
          ): ZIO[Has[transactor.Transactor[Task]], Nothing, List[FileId]] =
            for {
              children       <- filesRepo.listChildren(collectionId, Some(fileId)).orDie
              files = children.collect { case f: File => f.id }
              dirs = children.collect { case d: Directory => d }
              updated = collected.appendedAll(files)
              subdirsContent <- ZIO.foreachParN(3)(dirs)(dir => recursivelyGetChildFiles(collectionId, dir.id, updated))
            } yield files.appendedAll(subdirsContent.flatten)

          override def deleteVersionAs(
            userId: UserId,
            collectionId: CollectionId,
            fileId: FileId,
            versionId: FileVersionId
          ): ZIO[Any, DeleteVersionError, Unit] =
            db.transactionOrDie(
              for {
                _             <- authKernel.authorizeOperation(DeleteFile(Subject(userId.id), collectionId)).mapError(AuthorizationError)
                latestVersion <-
                  filesRepo.getFile(collectionId, fileId, None).orDie.someOrFail(FileNotFound(collectionId, fileId, None)).flatMap {
                    case f: File      => ZIO.succeed(f)
                    case _: Directory => ZIO.fail(NotAFile(collectionId, fileId))
                  }
                _             <-
                  ZIO.cond(versionId =!= latestVersion.versionId, (), DeleteLatestVersionImpossibleError(collectionId, fileId, versionId))
                _             <- filesRepo
                                   .getFile(collectionId, fileId, Some(versionId))
                                   .orDie
                                   .someOrFail(FileNotFound(collectionId, fileId, Some(versionId)))
                _             <- filesRepo.deleteVersion(fileId, versionId).orDie
                _             <- fs.deleteFileOrDirectory(filePath(collectionId, fileId, versionId), recursively = false).orDie
                _             <- logger.info(show"User $userId deleted version $versionId of file $fileId from collection $collectionId")
              } yield ()
            )

        }
    )

}
