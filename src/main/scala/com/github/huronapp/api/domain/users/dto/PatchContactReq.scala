package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.domain.users.dto.fields.ContactAlias
import com.github.huronapp.api.utils.OptionalValue
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class PatchContactReq(alias: Option[OptionalValue[ContactAlias]])

object PatchContactReq {

  implicit val codec: Codec[PatchContactReq] = deriveCodec[PatchContactReq]

  implicit val tapirSchema: Schema[PatchContactReq] = Schema.derived[PatchContactReq]

}
