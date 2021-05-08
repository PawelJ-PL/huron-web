package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

import java.time.Instant

final case class ApiKeyDataResp(
  id: FUUID,
  key: String,
  enabled: Boolean,
  description: String,
  validTo: Option[Instant],
  createdAt: Instant,
  updatedAt: Instant)

object ApiKeyDataResp {

  implicit val codec: Codec[ApiKeyDataResp] = deriveCodec[ApiKeyDataResp]

  implicit val tapirSchema: Schema[ApiKeyDataResp] = Schema.derived[ApiKeyDataResp]

}
