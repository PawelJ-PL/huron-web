package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.domain.users.Email
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class LoginReq(email: Email, password: String)

object LoginReq {

  implicit val codec: Codec[LoginReq] = deriveCodec

  implicit val tapirSchema: Schema[LoginReq] = Schema.derived[LoginReq]

}
