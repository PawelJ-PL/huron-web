package com.github.huronapp.api.http

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec
import io.circe.shapes._
import shapeless.{Coproduct, Generic}
import sttp.tapir.Schema

sealed trait ErrorResponse

object ErrorResponse {

  final case class BadRequest(message: String) extends ErrorResponse

  object BadRequest {

    implicit val codec: Codec[BadRequest] = deriveCodec

    implicit val tapirSchema: Schema[BadRequest] = Schema.derived

  }

  final case class PreconditionFailed(message: String, reason: Option[String]) extends ErrorResponse

  object PreconditionFailed {

    implicit val codec: Codec[PreconditionFailed] = deriveCodec

    implicit val tapirSchema: Schema[PreconditionFailed] = Schema.derived

  }

  final case class Conflict(message: String, reason: Option[String]) extends ErrorResponse

  object Conflict {

    implicit val codec: Codec[Conflict] = deriveCodec

    implicit val tapirSchema: Schema[Conflict] = Schema.derived

  }

  final case class NotFound(message: String) extends ErrorResponse

  object NotFound {

    implicit val codec: Codec[NotFound] = deriveCodec

    implicit val tapirSchema: Schema[NotFound] = Schema.derived

  }

  final case class Unauthorized(message: String) extends ErrorResponse

  object Unauthorized {

    implicit val codec: Codec[Unauthorized] = deriveCodec

    implicit val tapirSchema: Schema[Unauthorized] = Schema.derived

  }

  final case class Forbidden(message: String) extends ErrorResponse

  object Forbidden {

    implicit val codec: Codec[Forbidden] = deriveCodec

    implicit val tapirSchema: Schema[Forbidden] = Schema.derived

  }

  final case class UnprocessableEntity(message: String, reason: Option[String]) extends ErrorResponse

  object UnprocessableEntity {

    implicit val codec: Codec[UnprocessableEntity] = deriveCodec

    implicit val tapirSchema: Schema[UnprocessableEntity] = Schema.derived

  }

  def encodeAdtNoDiscr[Repr <: Coproduct](
    implicit
    gen: Generic.Aux[ErrorResponse, Repr],
    encodeRepr: Encoder[Repr]
  ): Encoder[ErrorResponse] = encodeRepr.contramap(gen.to)

  def decodeAdtNoDiscr[Repr <: Coproduct](
    implicit
    gen: Generic.Aux[ErrorResponse, Repr],
    decodeRepr: Decoder[Repr]
  ): Decoder[ErrorResponse] = decodeRepr.map(gen.from)

  implicit val decoder: Decoder[ErrorResponse] = decodeAdtNoDiscr

  implicit val encoder: Encoder[ErrorResponse] = encodeAdtNoDiscr

  implicit val tapirSchema: Schema[ErrorResponse] = Schema.derived

}
