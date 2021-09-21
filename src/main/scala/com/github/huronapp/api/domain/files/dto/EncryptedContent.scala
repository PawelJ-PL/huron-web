package com.github.huronapp.api.domain.files.dto

import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import com.github.huronapp.api.domain.files.dto.fields.{EncryptedBytes, EncryptedContentAlgorithm, Iv}
import io.chrisdavenport.fuuid.FUUID
import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import sttp.tapir.Schema

final case class EncryptedContent(algorithm: EncryptedContentAlgorithm, iv: Iv, encryptionKeyVersion: FUUID, bytes: EncryptedBytes)

object EncryptedContent {

  implicit val codec: Codec[EncryptedContent] = deriveCodec

  implicit val tapirSchema: Schema[EncryptedContent] = Schema.derived

}
