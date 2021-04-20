package com.github.huronapp.api.domain.users

import com.github.huronapp.api.utils.crypto.Crypto.Crypto
import com.github.huronapp.api.utils.crypto.{Crypto, DigestAlgo}
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import sttp.tapir.{Schema, Validator}
import zio.URIO

final case class Email(value: String) extends AnyVal {

  def normalized: String = value.trim.toLowerCase

  def digest: URIO[Crypto, String] = Crypto.digest(normalized, DigestAlgo.Sha256)

}

object Email {

  implicit val codec: Codec[Email] = deriveUnwrappedCodec

  implicit val tapirSchema: Schema[Email] = Schema
    .schemaForString
    .map(value => Some(Email(value)))(_.value)
    .validate(
      Validator
        .pattern[String]("^\\S+@\\S+$")
        .contramap(_.value)
    )

}
