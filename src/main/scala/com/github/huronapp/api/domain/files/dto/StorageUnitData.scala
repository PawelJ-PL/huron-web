package com.github.huronapp.api.domain.files.dto

import com.github.huronapp.api.domain.files.{Directory, File, StorageUnit}
import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.generic.semiauto.deriveCodec
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl._
import sttp.tapir.generic.{Configuration => SchemaConfiguration}
import sttp.tapir.Schema

import java.time.Instant

sealed trait StorageUnitData

object StorageUnitData {

  implicit val circeConfig: Configuration = Configuration.default.withDiscriminator("@type")

  implicit val schemaConfig: SchemaConfiguration = SchemaConfiguration.default.withDiscriminator("@type")

  implicit val codec: Codec[StorageUnitData] = deriveConfiguredCodec

  implicit val tapirSchema: Schema[StorageUnitData] = Schema.derived

  def fromDomain(storageUnit: StorageUnit): StorageUnitData =
    storageUnit match {
      case file: File           => file.transformInto[FileData]
      case directory: Directory => directory.transformInto[DirectoryData]
    }

}

final case class FileData(
  id: FUUID,
  collectionId: FUUID,
  parent: Option[FUUID],
  name: String,
  versionId: FUUID,
  versionAuthor: Option[FUUID],
  mimeType: Option[String],
  contentDigest: String,
  updatedAt: Instant)
    extends StorageUnitData

object FileData {

  implicit val codec: Codec[FileData] = deriveCodec

  implicit val tapirSchema: Schema[FileData] = Schema.derived

  implicit val fromDomainTransformer: Transformer[File, FileData] = Transformer
    .define[File, FileData]
    .withFieldComputed(_.id, _.id.id)
    .withFieldComputed(_.parent, _.parentId.map(_.id))
    .withFieldComputed(_.collectionId, _.collectionId.id)
    .withFieldComputed(_.versionId, _.versionId.id)
    .withFieldComputed(_.versionAuthor, _.versionAuthor.map(_.id))
    .withFieldComputed(_.contentDigest, _.originalDigest)
    .buildTransformer

}

final case class DirectoryData(id: FUUID, collectionId: FUUID, parent: Option[FUUID], name: String) extends StorageUnitData

object DirectoryData {

  implicit val codec: Codec[DirectoryData] = deriveCodec

  implicit val tapirSchema: Schema[DirectoryData] = Schema.derived

  implicit val fromDomainTransformer: Transformer[Directory, DirectoryData] = Transformer
    .define[Directory, DirectoryData]
    .withFieldComputed(_.id, _.id.id)
    .withFieldComputed(_.parent, _.parentId.map(_.id))
    .withFieldComputed(_.collectionId, _.collectionId.id)
    .buildTransformer

}
