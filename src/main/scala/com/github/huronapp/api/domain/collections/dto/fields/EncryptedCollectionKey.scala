package com.github.huronapp.api.domain.collections.dto.fields

import com.github.huronapp.api.utils.crypto.EncryptedStringPattern
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

final case class EncryptedCollectionKey(value: String) extends AnyVal

object EncryptedCollectionKey {

  implicit val codec: Codec[EncryptedCollectionKey] = deriveUnwrappedCodec[EncryptedCollectionKey]

  private val validator: Validator[EncryptedCollectionKey] = (
    Validator.maxLength[String](150) and
      Validator.pattern(EncryptedStringPattern)
  ).contramap(_.value)

  implicit val tapirSchema: Schema[EncryptedCollectionKey] = Schema.derived[EncryptedCollectionKey].validate(validator)

}
