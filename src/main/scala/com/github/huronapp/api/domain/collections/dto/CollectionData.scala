package com.github.huronapp.api.domain.collections.dto

import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema


final case class CollectionData(id: FUUID, name: String, encryptionKeyVersion: FUUID)

object CollectionData {

  implicit val codec: Codec[CollectionData] = deriveCodec[CollectionData]

  implicit val tapirSchema: Schema[CollectionData] = Schema.derived[CollectionData]

}
