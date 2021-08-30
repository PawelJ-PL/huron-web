package com.github.huronapp.api.domain.files.dto.fields

import io.circe.{Decoder, Encoder}
import org.bouncycastle.util.encoders.Hex
import sttp.tapir.{Schema, Validator}

import scala.util.Try

final case class EncryptedBytes(value: Array[Byte]) extends AnyVal

object EncryptedBytes {

  implicit val encoder: Encoder[EncryptedBytes] = Encoder.encodeString.contramap(bytes => Hex.toHexString(bytes.value))

  implicit val decoder: Decoder[EncryptedBytes] = Decoder.decodeString.emapTry(hex => Try(Hex.decode(hex)).map(EncryptedBytes(_)))

  private val validator: Validator[String] =
    Validator.pattern[String]("^(?:[a-fA-F0-9]{2})+$") and Validator.maxLength(1024 * 1024 * 10 * 2)

  implicit val tapirSchema: Schema[EncryptedBytes] =
    Schema
      .schemaForString
      .validate(validator)
      .map(hex => Try(Hex.decode(hex)).toOption.map(EncryptedBytes(_)))(bytes => Hex.toHexString(bytes.value))

}
