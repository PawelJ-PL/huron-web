package com.github.huronapp.api

import cats.syntax.either._
import ciris.{ConfigDecoder, ConfigError}
import org.http4s.Uri

import java.time.{Duration => JDuration}
import scala.concurrent.duration.FiniteDuration

package object config {

  object instances {

    implicit val uriDecoder: ConfigDecoder[String, Uri] =
      ConfigDecoder.instance((maybeKey, value) => Uri.fromString(value).leftMap(err => ConfigError(s"$maybeKey: " + err.message)))

    implicit val javaDurationDecoder: ConfigDecoder[String, JDuration] =
      ConfigDecoder[String, FiniteDuration].map(finiteDuration => JDuration.ofNanos(finiteDuration.toNanos))

  }

}
