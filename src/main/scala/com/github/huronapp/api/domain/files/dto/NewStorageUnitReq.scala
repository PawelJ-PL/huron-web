package com.github.huronapp.api.domain.files.dto

import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import com.github.huronapp.api.domain.files.dto.fields.{ContentDigest, Description, FileName, MimeType}
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Codec, Decoder, Encoder, Json}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import sttp.tapir.generic.{Configuration => SchemaConfiguration}
import sttp.tapir.Schema

sealed trait NewStorageUnitReq

object NewStorageUnitReq {

  implicit val circeConfig: Configuration = Configuration.default.withDiscriminator("@type")

  implicit val schemaConfig: SchemaConfiguration = SchemaConfiguration.default.withDiscriminator("@type")

  implicit val codec: Codec[NewStorageUnitReq] = deriveConfiguredCodec

  implicit val tapirSchema: Schema[NewStorageUnitReq] = Schema.derived

}

final case class NewFile(
  parent: Option[FUUID],
  name: FileName,
  description: Option[Description],
  mimeType: Option[MimeType],
  content: EncryptedContent,
  contentDigest: ContentDigest)
    extends NewStorageUnitReq

object NewFile {

  implicit val encoder: Encoder.AsObject[NewFile] =
    deriveEncoder[NewFile].mapJsonObject(_.add("@type", Json.fromString("NewFile")))

  implicit val decoder: Decoder[NewFile] = deriveDecoder

  implicit val tapirSchema: Schema[NewFile] = Schema.derived

}

final case class NewDirectory(parent: Option[FUUID], name: FileName) extends NewStorageUnitReq

object NewDirectory {

  implicit val encoder: Encoder.AsObject[NewDirectory] =
    deriveEncoder[NewDirectory].mapJsonObject(_.add("@type", Json.fromString("NewDirectory")))

  implicit val decoder: Decoder[NewDirectory] = deriveDecoder

  implicit val tapirSchema: Schema[NewDirectory] = Schema.derived

}
