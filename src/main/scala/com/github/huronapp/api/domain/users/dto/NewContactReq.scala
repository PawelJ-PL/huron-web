package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.domain.users.dto.fields.ContactAlias
import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class NewContactReq(contactUserId: FUUID, alias: Option[ContactAlias])

object NewContactReq {

  implicit val codec: Codec[NewContactReq] = deriveCodec[NewContactReq]

  implicit val tapirSchema: Schema[NewContactReq] = Schema.derived[NewContactReq]

}
