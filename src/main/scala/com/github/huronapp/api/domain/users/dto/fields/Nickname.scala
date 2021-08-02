package com.github.huronapp.api.domain.users.dto.fields

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

final case class Nickname(value: String) extends AnyVal

object Nickname {

  implicit val codec: Codec[Nickname] = deriveUnwrappedCodec[Nickname]

  private val validator: Validator[Nickname] = (
    Validator.minLength[String](3) and
      Validator.maxLength(30) and
      Validator.pattern("^\\p{L}+$")
  ).contramap(_.value)

  implicit val tapirSchema: Schema[Nickname] = Schema.derived[Nickname].validate(validator)

}
