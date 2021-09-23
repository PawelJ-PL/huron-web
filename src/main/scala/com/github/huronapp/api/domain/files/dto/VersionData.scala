package com.github.huronapp.api.domain.files.dto

import com.github.huronapp.api.domain.files.File
import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.scalaland.chimney.Transformer
import sttp.tapir.Schema

import java.time.Instant

final case class VersionData(
  id: FUUID,
  collectionId: FUUID,
  versionId: FUUID,
  versionAuthor: Option[FUUID],
  mimeType: Option[String],
  contentDigest: String,
  encryptedSize: Long,
  updatedAt: Instant)

object VersionData {

  implicit val codec: Codec[VersionData] = deriveCodec

  implicit val tapirSchema: Schema[VersionData] = Schema.derived

  implicit val fromDomainTransformer: Transformer[File, VersionData] = Transformer
    .define[File, VersionData]
    .withFieldComputed(_.id, _.id.id)
    .withFieldComputed(_.collectionId, _.collectionId.id)
    .withFieldComputed(_.versionId, _.versionId.id)
    .withFieldComputed(_.versionAuthor, _.versionAuthor.map(_.id))
    .withFieldComputed(_.contentDigest, _.originalDigest)
    .withFieldComputed(_.encryptedSize, _.size)
    .buildTransformer

}
