package com.github.huronapp.api.utils.outbox

import io.circe
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Encoder}
import io.circe.parser

sealed trait OutboxCommand {

  val render: String = Encoder[OutboxCommand].apply(this).noSpaces

}

object OutboxCommand {

  def fromString(input: String): Either[circe.Error, OutboxCommand] = parser.parse(input).flatMap(_.as[OutboxCommand])

  implicit val circeConfig: Configuration = Configuration.default.withDiscriminator("@type")

  implicit val codec: Codec[OutboxCommand] = deriveConfiguredCodec

  final case class DeleteFiles(paths: List[String], recursively: Boolean) extends OutboxCommand

  object DeleteFiles {

    implicit val codec: Codec[DeleteFiles] = deriveCodec

  }

}
