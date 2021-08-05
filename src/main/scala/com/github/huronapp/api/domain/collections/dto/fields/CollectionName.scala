package com.github.huronapp.api.domain.collections.dto.fields

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

case class CollectionName(value: String) extends AnyVal

object CollectionName {

  implicit val codec: Codec[CollectionName] = deriveUnwrappedCodec[CollectionName]

  private val validator: Validator[CollectionName] = (
    Validator.minLength[String](3) and
      Validator.maxLength(30) and
      Validator.pattern("^[\\p{L}0-9_ ]+$")
  ).contramap(_.value)

  implicit val tapirSchema: Schema[CollectionName] = Schema.derived[CollectionName].validate(validator)

}
