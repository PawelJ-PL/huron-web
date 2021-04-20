package com.github.huronapp.api.http

import sttp.model.StatusCode
import sttp.tapir.EndpointOutput
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._

trait BaseEndpoint {

  protected val apiEndpoint: ZEndpoint[Unit, Unit, Unit] = endpoint.in("api" / "v1")

  protected val badRequest: EndpointOutput.OneOfMapping[ErrorResponse.BadRequest] =
    oneOfMapping(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description("Wrong input parameters"))

  protected val unauthorized: EndpointOutput.OneOfMapping[ErrorResponse.Unauthorized] =
    oneOfMapping(StatusCode.Unauthorized, jsonBody[ErrorResponse.Unauthorized].description("User not authenticated"))

}
