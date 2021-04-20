package com.github.huronapp.api.utils

import cats.syntax.either._
import ciris.{ConfigDecoder, ConfigError}
import com.vdurmont.semver4j.Semver
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

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

  }

}
