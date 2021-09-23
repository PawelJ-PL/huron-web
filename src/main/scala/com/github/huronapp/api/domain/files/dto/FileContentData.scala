package com.github.huronapp.api.domain.files.dto

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class FileContentData(content: EncryptedContent, digest: String, name: String, mimeType: Option[String])

object FileContentData {

  implicit val codec: Codec[FileContentData] = deriveCodec

  implicit val tapirSchema: Schema[FileContentData] = Schema.derived

}
