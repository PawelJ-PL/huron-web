package com.github.huronapp.api.domain.users.dto.fields

import com.github.huronapp.api.utils.TapirValidators
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

final case class Password(value: String) extends AnyVal

object Password {

  implicit val codec: Codec[Password] = deriveUnwrappedCodec[Password]

  private val validator: Validator[Password] = (Validator.minLength[String](15) and TapirValidators.nonEmptyString).contramap(_.value)

  implicit val tapirSchema: Schema[Password] = Schema.derived[Password].validate(validator)

}
