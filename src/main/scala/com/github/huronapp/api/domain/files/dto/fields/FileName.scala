package com.github.huronapp.api.domain.files.dto.fields

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

case class FileName(value: String) extends AnyVal

object FileName {

  implicit val codec: Codec[FileName] = deriveUnwrappedCodec

  private val validator: Validator[FileName] = (
    Validator.minLength[String](1) and
      Validator.maxLength(255) and
      Validator.pattern("^[^/]+$")
  ).contramap(_.value)

  implicit val tapirSchema: Schema[FileName] = Schema.derived.validate(validator)

}
