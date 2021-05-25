package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.domain.users.dto.fields.ApiKeyDescription
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

import java.time.Instant

final case class NewPersonalApiKeyReq(description: ApiKeyDescription, validTo: Option[Instant])

object NewPersonalApiKeyReq {

  implicit val codec: Codec[NewPersonalApiKeyReq] = deriveCodec

  implicit val tapirSchema: Schema[NewPersonalApiKeyReq] = Schema.derived[NewPersonalApiKeyReq]

}
