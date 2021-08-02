package com.github.huronapp.api.domain.users.dto.fields

import com.github.huronapp.api.domain.users.KeyAlgorithm
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.{Schema, Validator}
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum

final case class KeyPair(algorithm: KeyAlgorithm, publicKey: PublicKey, encryptedPrivateKey: PrivateKey)

object KeyPair extends TapirCodecEnumeratum {

  implicit val codec: Codec[KeyPair] = deriveCodec[KeyPair]

  implicit val tapirSchema: Schema[KeyPair] = Schema.derived[KeyPair]

}

final case class PublicKey(value: String) extends AnyVal

object PublicKey {

  implicit val codec: Codec[PublicKey] = deriveUnwrappedCodec[PublicKey]

  private val validator: Validator[PublicKey] = (Validator.maxLength[String](2000) and
    Validator.pattern(
      "(-----BEGIN PUBLIC KEY-----(\\n|\\r|\\r\\n)([0-9a-zA-Z\\+\\/=]{64}(\\n|\\r|\\r\\n))*([0-9a-zA-Z\\+\\/=]{1,63}(\\n|\\r|\\r\\n))?-----END PUBLIC KEY-----)"
    ))
    .contramap(_.value)

  implicit val tapirSchema: Schema[PublicKey] = Schema.derived[PublicKey].validate(validator)

}

final case class PrivateKey(value: String) extends AnyVal

object PrivateKey {

  implicit val codec: Codec[PrivateKey] = deriveUnwrappedCodec[PrivateKey]

  private val validator: Validator[PrivateKey] = (
    Validator.maxLength[String](8000) and
      Validator.pattern("^AES-CBC:\\d{2}:\\d{2}:[0-9a-zA-Z]+:[0-9a-zA-Z]+$")
  ).contramap(_.value)

  implicit val tapirSchema: Schema[PrivateKey] = Schema.derived[PrivateKey].validate(validator)

}
