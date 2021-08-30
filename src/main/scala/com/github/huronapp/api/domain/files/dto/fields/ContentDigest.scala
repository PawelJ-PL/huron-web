package com.github.huronapp.api.domain.files.dto.fields

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

final case class ContentDigest(value: String) extends AnyVal

object ContentDigest {

  implicit val codec: Codec[ContentDigest] = deriveUnwrappedCodec

  private val validator: Validator[ContentDigest] =
    (Validator.minLength[String](64) and
      Validator.maxLength(64) and
      Validator.pattern("^[a-fA-F0-9]+$"))
      .contramap(_.value)

  implicit val tapirSchema: Schema[ContentDigest] = Schema.derived.validate(validator)

}
