package com.github.huronapp.api.domain.collections.dto

import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class UserCollectionData(id: FUUID, name: String, encryptionKeyVersion: FUUID, owner: FUUID, isAccepted: Boolean)

object UserCollectionData {

  implicit val codec: Codec[UserCollectionData] = deriveCodec[UserCollectionData]

  implicit val tapirSchema: Schema[UserCollectionData] = Schema.derived[UserCollectionData]

}
