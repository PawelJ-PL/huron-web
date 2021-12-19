package com.github.huronapp.api.http

import sttp.model.StatusCode
import sttp.tapir.{EndpointOutput, PublicEndpoint}
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._

trait BaseEndpoint {

  protected def publicApiEndpoint: PublicEndpoint[Unit, Unit, Unit, Any] = endpoint.in("api" / "v1")

  protected val badRequest: EndpointOutput.OneOfVariant[ErrorResponse.BadRequest] =
    oneOfVariant(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description("Wrong input parameters"))

  protected val unauthorized: EndpointOutput.OneOfVariant[ErrorResponse.Unauthorized] =
    oneOfVariant(StatusCode.Unauthorized, jsonBody[ErrorResponse.Unauthorized].description("User not authenticated"))

  protected val forbidden: EndpointOutput.OneOfVariant[ErrorResponse.Forbidden] =
    oneOfVariant(StatusCode.Forbidden, jsonBody[ErrorResponse.Forbidden].description("Operation not permitted"))

}
