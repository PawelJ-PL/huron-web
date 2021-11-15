package com.github.huronapp.api.domain.users.dto.fields

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

final case class ContactAlias(value: String) extends AnyVal

object ContactAlias {

  implicit val codec: Codec[ContactAlias] = deriveUnwrappedCodec[ContactAlias]

  private val validator: Validator[ContactAlias] = (
    Validator.minLength[String](5) and
      Validator.maxLength(40) and
      Validator.pattern("^[\\p{L}|0-9]+$")
  ).contramap(_.value)

  implicit val tapirSchema: Schema[ContactAlias] = Schema.derived[ContactAlias].validate(validator)

}
