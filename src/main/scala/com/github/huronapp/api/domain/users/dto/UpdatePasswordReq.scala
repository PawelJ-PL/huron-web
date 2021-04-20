package com.github.huronapp.api.domain.users.dto

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class UpdatePasswordReq(currentPassword: String, newPassword: String)

object UpdatePasswordReq {

  implicit val codec: Codec[UpdatePasswordReq] = deriveCodec[UpdatePasswordReq]

  implicit val tapirSchema: Schema[UpdatePasswordReq] = Schema.derived[UpdatePasswordReq]

}
