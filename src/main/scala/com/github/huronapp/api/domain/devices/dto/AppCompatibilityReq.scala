package com.github.huronapp.api.domain.devices.dto

import com.github.huronapp.api.utils.Implicits.semver._
import com.vdurmont.semver4j.Semver
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class AppCompatibilityReq(appVersion: Semver)

object AppCompatibilityReq {

  implicit val codec: Codec[AppCompatibilityReq] = deriveCodec

  implicit val tapirSchema: Schema[AppCompatibilityReq] = Schema.derived

}
