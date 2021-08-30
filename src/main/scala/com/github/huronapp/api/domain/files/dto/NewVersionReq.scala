package com.github.huronapp.api.domain.files.dto

import com.github.huronapp.api.domain.files.dto.fields.{ContentDigest, MimeType}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class NewVersionReq(mimeType: Option[MimeType], content: EncryptedContent, contentDigest: ContentDigest)

object NewVersionReq {

  implicit val codec: Codec[NewVersionReq] = deriveCodec

  implicit val tapirSchema: Schema[NewVersionReq] = Schema.derived

}
