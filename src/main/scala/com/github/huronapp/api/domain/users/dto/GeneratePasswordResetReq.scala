package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.domain.users.Email
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class GeneratePasswordResetReq(email: Email)

object GeneratePasswordResetReq {

  implicit val codec: Codec[GeneratePasswordResetReq] = deriveCodec[GeneratePasswordResetReq]

  implicit val tapirSchema: Schema[GeneratePasswordResetReq] = Schema.derived[GeneratePasswordResetReq]

}
