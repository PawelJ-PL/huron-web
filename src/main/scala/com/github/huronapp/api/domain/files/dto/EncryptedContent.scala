package com.github.huronapp.api.domain.files.dto

import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import cats.syntax.either._
import cats.syntax.eq._
import cats.syntax.show._
import com.github.huronapp.api.domain.files.dto.fields.{EncryptedBytes, EncryptedContentAlgorithm, Iv}
import com.github.huronapp.api.utils.crypto.EncryptedStringPatternWithKeyId
import io.chrisdavenport.fuuid.FUUID
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}
import org.bouncycastle.util.encoders.Hex
import sttp.tapir.{Schema, Validator}

import java.util.regex.Pattern

final case class EncryptedContent(algorithm: EncryptedContentAlgorithm, iv: Iv, encryptionKeyVersion: FUUID, bytes: EncryptedBytes)

object EncryptedContent {

  implicit val codec: Codec[EncryptedContent] = deriveCodec

  implicit val tapirSchema: Schema[EncryptedContent] = Schema.derived

//  private def contentToString(content: EncryptedContent) =
//    s"${content.algorithm}:${content.iv}:${content.encryptionKeyVersion.show}:${Hex.toHexString(content.bytes)}"
//
//  private val hexPattern = Pattern.compile("^(?:[a-fA-F0-9]{2})+$")
//
//  private def stringToContent(string: String): Either[String, EncryptedContent] = {
//    val MaxLength = 1024 * 1024 * 10
//
//    (string.split(":", 4) match {
//      case Array(algorithm, iv, keyVersion, ciphertext) => Right((algorithm, iv, keyVersion, ciphertext))
//      case _                                            => Left(show"$string is not valid content")
//    }).flatMap {
//      case (algorithm, iv, keyVersion, ciphertext) =>
//        FUUID.fromString(keyVersion).bimap(_.getMessage, uuidKey => (algorithm, iv, uuidKey, ciphertext))
//    }.flatMap {
//      case (algorithm, iv, keyVersion, ciphertext) =>
//        if (string.length > MaxLength)
//          Left(s"Content too big")
//        else if (algorithm =!= "AES-CBC")
//          Left(s"$algorithm is not valid algorithm")
//        else if (!hexPattern.matcher(iv).matches() || iv.length =!= 32)
//          Left(s"$iv is not valid IV")
//        else if (!hexPattern.matcher(ciphertext).matches())
//          Left(s"$ciphertext is not valid ciphertext")
//        else if (ciphertext.length > MaxLength)
//          Left("Content too big")
//        else
//          Right(FileContent(algorithm, iv, keyVersion, Hex.decode(ciphertext)))
//    }
//  }
//
//  implicit val encoder: Encoder[EncryptedContent] = Encoder.encodeString.contramap(contentToString)
//
//  implicit val decoder: Decoder[EncryptedContent] = Decoder.decodeString.emap(stringToContent)
//
//  private val validator: Validator[EncryptedContent] =
//    Validator.pattern[String](EncryptedStringPatternWithKeyId).contramap(contentToString)
//
//  implicit val tapirSchema: Schema[EncryptedContent] =
//    Schema.schemaForString.map(string => stringToContent(string).toOption)(contentToString).validate(validator)

}
