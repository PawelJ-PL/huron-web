package com.github.huronapp.api.domain.files

import cats.Eq
import cats.syntax.eq._
import com.github.huronapp.api.database.BasePostgresRepository
import com.github.huronapp.api.domain.collections.CollectionId
import com.github.huronapp.api.domain.users.UserId
import doobie.util.transactor
import io.chrisdavenport.fuuid.FUUID
import io.getquill.{MappedEncoding, Ord}
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie.{Connection, tzio}
import zio.clock.Clock
import zio.{Has, Task, ZIO, ZLayer}

import java.time.Instant

object FilesMetadataRepository {

  type FilesMetadataRepository = Has[FilesMetadataRepository.Service]

  trait Service {

    def createDirectory(directory: Directory): ZIO[Connection, DbException, Directory]

    def createFile(file: File): ZIO[Connection, DbException, File]

    def addLatestVersion(file: File): ZIO[Connection, DbException, File]

    def listChildren(collectionId: CollectionId, parent: Option[FileId]): ZIO[Connection, DbException, List[StorageUnit]]

    def getAllVersions(collectionId: CollectionId, fileId: FileId): ZIO[Connection, DbException, List[File]]

    def getFile(
      collectionId: CollectionId,
      fileId: FileId,
      version: Option[FileVersionId]
    ): ZIO[Connection, DbException, Option[StorageUnit]]

    def getFileByNameIn(
      collectionId: CollectionId,
      parentId: Option[FileId],
      fileName: String
    ): ZIO[Connection, DbException, Option[StorageUnit]]

    def deleteFile(fileId: FileId): ZIO[Connection, DbException, Unit]

    def deleteVersion(fileId: FileId, versionId: FileVersionId): ZIO[Connection, DbException, Unit]

    def updateFileMetadata(
      collectionId: CollectionId,
      fileId: FileId,
      newParent: Option[Option[FileId]],
      newName: Option[String],
      newDescription: Option[Option[String]]
    ): ZIO[Connection, DbException, Boolean]

  }

  val postgres: ZLayer[Clock, Nothing, FilesMetadataRepository] = ZLayer.fromService(clock =>
    new Service with BasePostgresRepository {
      import doobieContext._
      import dbImplicits._

      private def toFileMetadataEntity(storageUnit: StorageUnit, now: Instant) = {
        val description = storageUnit match {
          case file: File   => file.description
          case _: Directory => None
        }

        FileMetadataEntity(
          storageUnit.id.id,
          ObjectType.fromStorageUnit(storageUnit),
          storageUnit.collectionId.id,
          storageUnit.parentId.map(_.id),
          storageUnit.name,
          description,
          now
        )
      }

      override def createFile(file: File): ZIO[Connection, DbException, File] =
        for {
          now <- clock.instant
          fileEntity = toFileMetadataEntity(file, now)
          versionEntity = FileVersionEntity(
                            file.versionId.id,
                            file.id.id,
                            file.versionAuthor.map(_.id),
                            file.size,
                            file.mimeType,
                            file.originalDigest,
                            file.encryptedDigest,
                            file.encryptionParams.algorithm,
                            file.encryptionParams.iv,
                            file.encryptionParams.encryptionKeyVersion,
                            now
                          )
          latestVersionEntity = LatestVersionMappingEntity(file.id.id, file.versionId.id)
          _   <- tzio(run(filesMetadata.insert(lift(fileEntity))))
          _   <- tzio(run(fileVersions.insert(lift(versionEntity))))
          _   <- tzio(run(latestVersions.insert(lift(latestVersionEntity))))
        } yield file.copy(createdAt = now, updatedAt = now)

      override def createDirectory(directory: Directory): ZIO[Connection, DbException, Directory] =
        for {
          now <- clock.instant
          directoryEntity = toFileMetadataEntity(directory, now)
          _   <- tzio(run(filesMetadata.insert(lift(directoryEntity))))
        } yield directory.copy(createdAt = now)

      override def listChildren(
        collectionId: CollectionId,
        parent: Option[FileId]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[StorageUnit]] =
        tzio(
          run(
            quote(
              filesMetadata
//                .filter(file => file.collectionId == lift(collectionId.id) && file.parentId == lift(parent.map(_.id)))
                .filter(f =>
                  f.collectionId == lift(collectionId.id) && infix"${f.parentId} IS NOT DISTINCT FROM ${lift(parent.map(_.id))}"
                    .pure
                    .as[Boolean]
                ) //HACK: https://github.com/getquill/quill/issues/2052)
                .leftJoin(latestVersions)
                .on((metadata, latest) => metadata.id == latest.fileId)
                .leftJoin(fileVersions)
                .on((fileWithLatest, version) => fileWithLatest._2.map(_.versionId).contains(version.versionId))
                .sortBy(result => (result._1._1.`type`, result._1._1.fileName))(Ord(Ord.asc, Ord.asc))
            )
          ).map(_.collect {
            case ((file, _), Some(version))                                          => fileWithVersion(file, version)
            case ((directory, _), None) if directory.`type` === ObjectType.Directory =>
              Directory(
                FileId(directory.id),
                CollectionId(directory.collectionId),
                directory.parentId.map(FileId(_)),
                directory.fileName,
                directory.createdAt
              )
          })
        )

      private def fileWithVersion(fileMetadataEntity: FileMetadataEntity, versionEntity: FileVersionEntity): File =
        File(
          FileId(fileMetadataEntity.id),
          CollectionId(fileMetadataEntity.collectionId),
          fileMetadataEntity.parentId.map(FileId(_)),
          fileMetadataEntity.fileName,
          fileMetadataEntity.description,
          FileVersionId(versionEntity.versionId),
          versionEntity.createdBy.map(UserId(_)),
          versionEntity.size,
          versionEntity.mimeType,
          versionEntity.originalDigest,
          versionEntity.encryptedDigest,
          EncryptionParams(versionEntity.encryptionAlgorithm, versionEntity.encryption_iv, versionEntity.encryptionKeyVersion),
          fileMetadataEntity.createdAt,
          versionEntity.createdAt
        )

      override def getAllVersions(
        collectionId: CollectionId,
        fileId: FileId
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[File]] =
        tzio(
          run(
            quote(
              for {
                versions <- fileVersions.filter(_.fileId == lift(fileId.id))
                metadata <- filesMetadata
                              .filter(f => f.id == lift(fileId.id) && f.collectionId == lift(collectionId.id))
                              .join(_.id == versions.fileId)
              } yield (metadata, versions)
            ).sortBy(_._2.createdAt)(Ord.desc)
          ).map(_.map {
            case (metadata, version) => fileWithVersion(metadata, version)
          })
        )

      override def getFile(
        collectionId: CollectionId,
        fileId: FileId,
        version: Option[FileVersionId]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[StorageUnit]] =
        version match {
          case Some(value) => getFileWithVersion(collectionId, fileId, value)
          case None        => getFileWithLatestVersion(collectionId, fileId)
        }

      private def getFileWithVersion(
        collectionId: CollectionId,
        fileId: FileId,
        version: FileVersionId
      ): ZIO[Connection, DbException, Option[File]] =
        tzio(
          run(
            quote(
              for {
                metadata <- filesMetadata.filter(f => f.collectionId == lift(collectionId.id) && f.id == lift(fileId.id))
                version  <- fileVersions.join(_.fileId == metadata.id).filter(_.versionId == lift(version.id))
              } yield (metadata, version)
            )
          ).map(_.headOption.map {
            case (file, version) => fileWithVersion(file, version)
          })
        )

      private def getFileWithLatestVersion(collectionId: CollectionId, fileId: FileId): ZIO[Connection, DbException, Option[StorageUnit]] =
        tzio(
          run(
            quote(
              filesMetadata
                .filter(f => f.collectionId == lift(collectionId.id) && f.id == lift(fileId.id))
                .leftJoin(latestVersions)
                .on((metadata, latest) => metadata.id == latest.fileId)
                .leftJoin(fileVersions)
                .on((fileWithLatest, version) => fileWithLatest._2.map(_.versionId).contains(version.versionId))
            )
          ).map(_.headOption.collect {
            case ((file, _), Some(version))                                          => fileWithVersion(file, version)
            case ((directory, _), None) if directory.`type` === ObjectType.Directory =>
              Directory(
                FileId(directory.id),
                CollectionId(directory.collectionId),
                directory.parentId.map(FileId(_)),
                directory.fileName,
                directory.createdAt
              )
          })
        )

      override def getFileByNameIn(
        collectionId: CollectionId,
        parentId: Option[FileId],
        fileName: String
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[StorageUnit]] =
        tzio(
          run(
            quote(
              filesMetadata
                .filter(f =>
                  f.collectionId == lift(collectionId.id) &&
                    infix"${f.parentId} IS NOT DISTINCT FROM ${lift(parentId.map(_.id))}"
                      .pure
                      .as[Boolean] && //HACK: https://github.com/getquill/quill/issues/2052
                    f.fileName == lift(fileName)
                )
                .map(_.id)
            )
          ).map(_.headOption)
        ).flatMap {
          case Some(id) => getFileWithLatestVersion(collectionId, FileId(id))
          case None     => ZIO.none
        }

      override def addLatestVersion(file: File): ZIO[Has[transactor.Transactor[Task]], DbException, File] =
        for {
          now <- clock.instant
          versionEntity = FileVersionEntity(
                            file.versionId.id,
                            file.id.id,
                            file.versionAuthor.map(_.id),
                            file.size,
                            file.mimeType,
                            file.originalDigest,
                            file.encryptedDigest,
                            file.encryptionParams.algorithm,
                            file.encryptionParams.iv,
                            file.encryptionParams.encryptionKeyVersion,
                            now
                          )
          _   <- tzio(run(quote(fileVersions.insert(lift(versionEntity)))))
          _   <- tzio(run(quote(latestVersions.filter(_.fileId == lift(file.id.id)).update(_.versionId -> lift(file.versionId.id)))))
        } yield file.copy(updatedAt = now)

      override def deleteFile(fileId: FileId): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        for {
          _ <- tzio(run(quote(latestVersions.filter(_.fileId == lift(fileId.id)).delete)))
          _ <- tzio(run(quote(fileVersions.filter(_.fileId == lift(fileId.id)).delete)))
          _ <- tzio(run(quote(filesMetadata.filter(_.id == lift(fileId.id)).delete)))
        } yield ()

      override def deleteVersion(fileId: FileId, versionId: FileVersionId): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        tzio(
          run(quote(fileVersions.filter(version => version.fileId == lift(fileId.id) && version.versionId == lift(versionId.id)).delete))
        ).unit

      override def updateFileMetadata(
        collectionId: CollectionId,
        fileId: FileId,
        newParent: Option[Option[FileId]],
        newName: Option[String],
        newDescription: Option[Option[String]]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        tzio(
          run(
            filesMetadata
              .dynamic
              .filter(f => f.collectionId == lift(collectionId.id) && f.id == lift(fileId.id))
              .update(
                setOpt(_.parentId, newParent.map(_.map(_.id))),
                setOpt(_.fileName, newName),
                setOpt(_.description, newDescription)
              )
          )
        ).map(_ > 0)

      private val filesMetadata = quote {
        querySchema[FileMetadataEntity]("files_metadata")
      }

      private val fileVersions = quote {
        querySchema[FileVersionEntity]("file_versions")
      }

      private val latestVersions = quote {
        querySchema[LatestVersionMappingEntity]("files_latest_versions")
      }

    }
  )

}

private sealed trait ObjectType

private object ObjectType {

  case object File extends ObjectType

  case object Directory extends ObjectType

  def fromStorageUnit(storageUnit: StorageUnit): ObjectType =
    storageUnit match {
      case _: File      => ObjectType.File
      case _: Directory => ObjectType.Directory
    }

  implicit val eq: Eq[ObjectType] = Eq.fromUniversalEquals

  implicit val encodeObjectType: MappedEncoding[ObjectType, String] = MappedEncoding[ObjectType, String](_.toString)

  implicit val decodeObjectType: MappedEncoding[String, ObjectType] = MappedEncoding[String, ObjectType] {
    case "File"      => ObjectType.File
    case "Directory" => ObjectType.Directory
    case other       => throw new RuntimeException(s"$other is not supported object type")
  }

}

private final case class FileMetadataEntity(
  id: FUUID,
  `type`: ObjectType,
  collectionId: FUUID,
  parentId: Option[FUUID],
  fileName: String,
  description: Option[String],
  createdAt: Instant)

private final case class FileVersionEntity(
  versionId: FUUID,
  fileId: FUUID,
  createdBy: Option[FUUID],
  size: Long,
  mimeType: Option[String],
  originalDigest: String,
  encryptedDigest: String,
  encryptionAlgorithm: String,
  encryption_iv: String,
  encryptionKeyVersion: FUUID,
  createdAt: Instant)

private final case class LatestVersionMappingEntity(fileId: FUUID, versionId: FUUID)
