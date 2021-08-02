package com.github.huronapp.api.domain.users.dto.fields

import com.github.huronapp.api.utils.TapirValidators
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

final case class ApiKeyDescription(value: String) extends AnyVal

object ApiKeyDescription {

  implicit val codec: Codec[ApiKeyDescription] = deriveUnwrappedCodec[ApiKeyDescription]

  private val validator: Validator[ApiKeyDescription] = (
    TapirValidators.nonEmptyString and
      Validator.pattern("^[\\p{L}0-9_ ]+$") and
      Validator.maxLength(80)
  ).contramap(_.value)

  implicit val tapirSchema: Schema[ApiKeyDescription] = Schema.derived[ApiKeyDescription].validate(validator)

}
