package com.github.huronapp.api.domain.users.dto

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class PublicUserContactResp(alias: Option[String])

object PublicUserContactResp {

  implicit val codec: Codec[PublicUserContactResp] = deriveCodec[PublicUserContactResp]

  implicit val tapirSchema: Schema[PublicUserContactResp] = Schema.derived[PublicUserContactResp]

}
