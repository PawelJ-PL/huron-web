package com.github.huronapp.api.domain.files.dto.fields

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}

final case class EncryptedContentAlgorithm(value: String) extends AnyVal

object EncryptedContentAlgorithm {

  implicit val codec: Codec[EncryptedContentAlgorithm] = deriveUnwrappedCodec

  private val validator: Validator[EncryptedContentAlgorithm] = Validator.`enum`(List("AES-CBC")).contramap(_.value)

  implicit val tapirSchema: Schema[EncryptedContentAlgorithm] = Schema.derived.validate(validator)

}
