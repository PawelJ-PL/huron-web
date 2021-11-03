package com.github.huronapp.api.http

import sttp.model.StatusCode
import sttp.tapir.{Endpoint, EndpointOutput}
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._

trait BaseEndpoint {

  protected val apiEndpoint: Endpoint[Unit, Unit, Unit, Any] = endpoint.in("api" / "v1")

  protected val badRequest: EndpointOutput.OneOfMapping[ErrorResponse.BadRequest] =
    oneOfMapping(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description("Wrong input parameters"))

  protected val unauthorized: EndpointOutput.OneOfMapping[ErrorResponse.Unauthorized] =
    oneOfMapping(StatusCode.Unauthorized, jsonBody[ErrorResponse.Unauthorized].description("User not authenticated"))

  protected val forbidden: EndpointOutput.OneOfMapping[ErrorResponse.Forbidden] =
    oneOfMapping(StatusCode.Forbidden, jsonBody[ErrorResponse.Forbidden].description("Operation not permitted"))

}
