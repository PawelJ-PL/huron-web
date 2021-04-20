package com.github.huronapp.api.domain.users.dto

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class PasswordResetReq(password: String)

object PasswordResetReq {

  implicit val codec: Codec[PasswordResetReq] = deriveCodec[PasswordResetReq]

  implicit val tapirSchema: Schema[PasswordResetReq] = Schema.derived[PasswordResetReq]

}
