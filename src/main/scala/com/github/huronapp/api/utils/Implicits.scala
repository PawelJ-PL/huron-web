package com.github.huronapp.api.utils

import cats.syntax.either._
import ciris.{ConfigDecoder, ConfigError}
import com.github.huronapp.api.domain.collections.CollectionId
import com.github.huronapp.api.domain.files.{FileId, FileVersionId}
import com.vdurmont.semver4j.Semver
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, Encoder}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema}

object Implicits {

  object semver {

    implicit val semverEncoder: Encoder[Semver] = Encoder.encodeString.contramap(_.getValue)

    implicit val semverDecoder: Decoder[Semver] = Decoder.decodeString.emap(SemverWrapper.of(_).leftMap(_.getMessage))

    implicit val semverTapirSchema: Schema[Semver] = Schema.string

    implicit val semverConfigDecoder: ConfigDecoder[String, Semver] =
      ConfigDecoder.instance((maybeKey, value) =>
        SemverWrapper.of(value).leftMap(err => ConfigError(maybeKey.toString + ": " + err.getMessage))
      )

  }

  object fuuid {

    implicit val fuuidTapirSchema: Schema[FUUID] = Schema.schemaForString.map(FUUID.fromStringOpt)(_.show)

    implicit val fuuidTapirTextPlainCodec: Codec[String, FUUID, CodecFormat.TextPlain] = Codec
      .string
      .mapDecode(value =>
        FUUID.fromString(value) match {
          case Left(error)  => DecodeResult.Error(value, error)
          case Right(fuuid) => DecodeResult.Value(fuuid)
        }
      )(_.show)

  }

  object collectionId {

    implicit val collectionIdTapirTextPlainCodec: Codec[String, CollectionId, CodecFormat.TextPlain] =
      fuuid.fuuidTapirTextPlainCodec.map(CollectionId(_))(_.id)

  }

  object fileId {

    implicit val fileIdTapirTextPlainCodec: Codec[String, FileId, CodecFormat.TextPlain] = fuuid.fuuidTapirTextPlainCodec.map(FileId(_))(_.id)

  }

  object versionId {
    implicit val versionIdTapirTextPlainCodec: Codec[String, FileVersionId, CodecFormat.TextPlain] = fuuid.fuuidTapirTextPlainCodec.map(FileVersionId(_))(_.id)
  }

}
