package com.github.huronapp.api.domain.files.dto.fields

import com.github.huronapp.api.utils.TapirValidators
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

final case class Description(value: String) extends AnyVal

object Description {

  implicit val codec: Codec[Description] = deriveUnwrappedCodec

  private val validator: Validator[Description] = (
    TapirValidators.nonEmptyString and
      Validator.pattern("^[\\p{L}0-9_ ]+$") and
      Validator.maxLength(255)
  ).contramap(_.value)

  implicit val tapirSchema: Schema[Description] = Schema.derived.validate(validator)

}
