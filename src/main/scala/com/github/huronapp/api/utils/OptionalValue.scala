package com.github.huronapp.api.utils

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

final case class OptionalValue[A](value: Option[A])

object OptionalValue {

  implicit val circeConfig: Configuration = Configuration.default.withStrictDecoding

  implicit def decoder[A: Decoder]: Decoder[OptionalValue[A]] = deriveConfiguredDecoder[OptionalValue[A]]

  implicit def encoder[A: Encoder]: Encoder[OptionalValue[A]] = deriveEncoder[OptionalValue[A]]

  implicit def tapirSchema[A: Schema]: Schema[OptionalValue[A]] = Schema.derived[OptionalValue[A]] // See https://github.com/softwaremill/tapir/issues/1303

}
