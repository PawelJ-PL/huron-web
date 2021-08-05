package com.github.huronapp.api.domain.collections.dto

import com.github.huronapp.api.domain.collections.dto.fields.{CollectionName, EncryptedCollectionKey}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class NewCollectionReq(name: CollectionName, encryptedKey: EncryptedCollectionKey)

object NewCollectionReq {

  implicit val codec: Codec[NewCollectionReq] = deriveCodec[NewCollectionReq]

  implicit val tapirSchema: Schema[NewCollectionReq] = Schema.derived[NewCollectionReq]

}
