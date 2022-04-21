package com.github.huronapp.api.testdoubles

import cats.syntax.eq._
import com.github.huronapp.api.constants.MiscConstants
import com.github.huronapp.api.domain.files.FilesMetadataRepository.FilesMetadataRepository
import com.github.huronapp.api.domain.{collections, files}
import com.github.huronapp.api.domain.files.{Directory, File, FileId, FilesMetadataRepository, StorageUnit}
import doobie.util.transactor
import io.github.gaelrenoux.tranzactio.DbException
import zio.{Has, Ref, Task, ULayer, ZIO, ZLayer}

object FilesMetadataRepositoryFake {

  def create(ref: Ref[List[StorageUnit]]): ULayer[FilesMetadataRepository] =
    ZLayer.succeed(new FilesMetadataRepository.Service with MiscConstants {

      override def createDirectory(directory: Directory): ZIO[Has[transactor.Transactor[Task]], DbException, Directory] = {
        val updated = directory.copy(createdAt = Now)
        ref.update(_.appended(updated)).as(updated)
      }

      override def createFile(file: File): ZIO[Has[transactor.Transactor[Task]], DbException, File] = {
        val updated = file.copy(createdAt = Now, updatedAt = Now)
        ref.update(_.appended(updated)).as(updated)
      }

      override def addLatestVersion(file: File): ZIO[Has[transactor.Transactor[Task]], DbException, File] =
        ref.modify { state =>
          val versionTimestamp = state
            .filter(f => f.id === file.id && f.collectionId === file.collectionId)
            .collect { case f: File => f }
            .maxByOption(_.updatedAt)
            .map(_.updatedAt.plusSeconds(3600))
            .getOrElse(Now)
          val updatedVersion = file.copy(updatedAt = versionTimestamp)
          val updatedState = state :+ updatedVersion
          (updatedVersion, updatedState)
        }

      override def listChildren(
        collectionId: collections.CollectionId,
        parent: Option[files.FileId]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[StorageUnit]] = ref.get.map(_.filter(_.parentId === parent))

      override def getAllVersions(
        collectionId: collections.CollectionId,
        fileId: files.FileId
      ): ZIO[Has[transactor.Transactor[Task]], DbException, List[File]] =
        ref
          .get
          .map(_.filter(f => f.id === fileId && f.collectionId === collectionId).collect { case f: File => f }.sortBy(_.updatedAt).reverse)

      override def getFile(
        collectionId: collections.CollectionId,
        fileId: files.FileId,
        version: Option[files.FileVersionId]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[StorageUnit]] =
        ref.get.map {
          state =>
            val expectedVersion = version match {
              case Some(value) => Some(value)
              case None        =>
                val fileVersions = state.filter(f => f.collectionId === collectionId && f.id === fileId).collect { case f: File => f }
                fileVersions.sortBy(_.updatedAt).reverse.headOption.map(_.versionId)
            }
            expectedVersion match {
              case Some(versionId) =>
                state
                  .filter(f => f.collectionId === collectionId && f.id === fileId)
                  .collect { case f: File => f }
                  .find(_.versionId === versionId)
              case None            => state.find(d => d.id === fileId && d.collectionId === collectionId)
            }
        }

      override def getFileByNameIn(
        collectionId: collections.CollectionId,
        parentId: Option[files.FileId],
        fileName: String
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Option[StorageUnit]] =
        ref.get.map(_.find(f => f.name === fileName && f.collectionId === collectionId && f.parentId === parentId))

      override def deleteFile(fileId: files.FileId): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        ref.update { state =>
          def findChildren(parentId: FileId, acc: List[FileId]): List[FileId] = {
            val children = state.filter(_.parentId === Some(parentId))
            if (children.isEmpty) acc :+ parentId else children.flatMap(child => findChildren(child.id, acc :+ child.id :+ parentId))
          }

          val toDelete = findChildren(fileId, List.empty)
          state.filterNot(f => toDelete.contains(f.id))
        }

      override def deleteVersion(
        fileId: files.FileId,
        versionId: files.FileVersionId
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Unit] =
        ref.update(_.collect { case f: File => f }.filterNot(f => f.id === fileId && f.versionId === versionId))

      override def updateFileMetadata(
        collectionId: collections.CollectionId,
        fileId: files.FileId,
        newParent: Option[Option[files.FileId]],
        newName: Option[String],
        newDescription: Option[Option[String]]
      ): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        ref.modify {
          state =>
            val maybeUpdatedObject = state.find(f => f.id === fileId && f.collectionId === collectionId).map {
              case f: File      =>
                f.copy(
                  parentId = newParent.getOrElse(f.parentId),
                  name = newName.getOrElse(f.name),
                  description = newDescription.getOrElse(f.description)
                )
              case d: Directory => d.copy(parentId = newParent.getOrElse(d.parentId), name = newName.getOrElse(d.name))
            }
            val updateState = maybeUpdatedObject.map(updatedObj => state.filterNot(_.id === updatedObj.id) :+ updatedObj).getOrElse(state)
            (maybeUpdatedObject.isDefined, updateState)
        }

      override def isCollectionEmpty(collectionId: collections.CollectionId): ZIO[Has[transactor.Transactor[Task]], DbException, Boolean] =
        ref.get.map(!_.exists(_.collectionId === collectionId))

    })

}
