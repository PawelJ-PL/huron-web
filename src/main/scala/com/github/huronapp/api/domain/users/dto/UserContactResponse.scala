package com.github.huronapp.api.domain.users.dto

import io.chrisdavenport.fuuid.FUUID
import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class UserContactResponse(userId: FUUID, nickName: String, alias: Option[String])

object UserContactResponse {

  implicit val codec: Codec[UserContactResponse] = deriveCodec[UserContactResponse]

  implicit val tapirSchema: Schema[UserContactResponse] = Schema.derived[UserContactResponse]

}
