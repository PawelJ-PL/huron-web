package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.domain.users.Email
import com.github.huronapp.api.domain.users.dto.fields.{KeyPair, Password}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class PasswordResetReq(password: Password, email: Email, keyPair: KeyPair)

object PasswordResetReq {

  implicit val codec: Codec[PasswordResetReq] = deriveCodec[PasswordResetReq]

  implicit val tapirSchema: Schema[PasswordResetReq] = Schema.derived[PasswordResetReq]

}
