package com.github.huronapp.api.domain.files.dto.fields

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

final case class Iv(value: String) extends AnyVal

object Iv {

  implicit val codec: Codec[Iv] = deriveUnwrappedCodec

  private val validator: Validator[Iv] = (
    Validator.pattern[String]("^(?:[a-fA-F0-9]{2})+$") and
      Validator.maxLength(32) and
      Validator.minLength(32)
  ).contramap(_.value)

  implicit val tapirSchema: Schema[Iv] = Schema.derived.validate(validator)

}
