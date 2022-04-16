package com.github.huronapp.api.http

import org.log4s.getLogger
import sttp.model.{Header, StatusCode}
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.{FailureMessages, respond}
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.{headers, statusCode}

object TapirHandlers {

  private[this] val logger = getLogger

  private def failureResponse(c: StatusCode, hs: List[Header], m: String): ValuedEndpointOutput[_] = {
    logger.warn(s"Unable to process request because of: $m")
    ValuedEndpointOutput(statusCode.and(headers).and(jsonBody[ErrorResponse]), (c, hs, ErrorResponse.BadRequest("Invalid request")))
  }

  def handler: DefaultDecodeFailureHandler =
    DefaultDecodeFailureHandler(
      respond = respond(_, badRequestOnPathErrorIfPathShapeMatches = true, badRequestOnPathInvalidIfPathShapeMatches = true),
      failureMessage = FailureMessages.failureMessage,
      response = failureResponse
    )

}
