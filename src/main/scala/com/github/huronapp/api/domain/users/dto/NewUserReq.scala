package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.domain.users.dto.fields.{Nickname, Password}
import com.github.huronapp.api.domain.users.{Email, Language}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum

final case class NewUserReq(nickName: Nickname, email: Email, password: Password, language: Option[Language])

object NewUserReq extends TapirCodecEnumeratum {

  implicit val codec: Codec[NewUserReq] = deriveCodec

  implicit val tapirSchema: Schema[NewUserReq] = Schema.derived

}
