package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class PublicUserDataResp(userId: FUUID, nickName: String, contactData: Option[PublicUserContactResp])

object PublicUserDataResp {

  implicit val codec: Codec[PublicUserDataResp] = deriveCodec[PublicUserDataResp]

  implicit val tapirSchema: Schema[PublicUserDataResp] = Schema.derived[PublicUserDataResp]

}
