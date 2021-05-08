package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.utils.OptionalValue
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

import java.time.Instant

final case class UpdateApiKeyDataReq(
  description: Option[String],
  enabled: Option[Boolean],
  validTo: Option[OptionalValue[Instant]])

object UpdateApiKeyDataReq {

  implicit val codec: Codec[UpdateApiKeyDataReq] = deriveCodec[UpdateApiKeyDataReq]

  implicit val tapirSchema: Schema[UpdateApiKeyDataReq] = Schema.derived[UpdateApiKeyDataReq]

}

