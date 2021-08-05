package com.github.huronapp.api.domain.collections.dto

import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class EncryptionKeyData(collectionId: FUUID, key: String, version: FUUID)

object EncryptionKeyData {

  implicit val codec: Codec[EncryptionKeyData] = deriveCodec[EncryptionKeyData]

  implicit val tapirSchema: Schema[EncryptionKeyData] = Schema.derived[EncryptionKeyData]

}
