package com.github.huronapp.api.domain.files.dto.fields

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

final case class MimeType(value: String) extends AnyVal

object MimeType {

  implicit val codec: Codec[MimeType] = deriveUnwrappedCodec

  private val validator: Validator[MimeType] = (
    Validator.pattern[String]("^\\w+/[-.\\w]+(?:\\+[-.\\w]+)?$") and
      Validator.maxLength(50)
  ).contramap(_.value)

  implicit val tapirSchema: Schema[MimeType] = Schema.derived.validate(validator)

}
