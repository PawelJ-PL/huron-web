package com.github.huronapp.api.domain.devices

import cats.data.NonEmptyList
import com.github.huronapp.api.domain.devices.dto.AppCompatibilityReq
import com.github.huronapp.api.http.{BaseEndpoint, ErrorResponse}
import com.vdurmont.semver4j.Semver
import sttp.model.StatusCode
import sttp.tapir.json.circe._
import sttp.tapir.ztapir._

object DevicesEndpoints extends BaseEndpoint {

  private val devicesEndpoint: ZEndpoint[Unit, Unit, Unit] = apiEndpoint.tag("devices").in("devices")

  val checkApiCompatibilityEndpoint: ZEndpoint[AppCompatibilityReq, ErrorResponse, Unit] = devicesEndpoint
    .summary("Check whether application version is compatible with API")
    .post
    .in("compatibility")
    .in(jsonBody[AppCompatibilityReq].example(AppCompatibilityReq(new Semver("1.0.0"))))
    .out(statusCode(StatusCode.Ok))
    .errorOut(
      oneOf[ErrorResponse](
        badRequest,
        oneOfMapping(StatusCode.PreconditionFailed, jsonBody[ErrorResponse.PreconditionFailed].description("Incompatible version"))
      )
    )

  val endpoints: NonEmptyList[ZEndpoint[_, _, _]] = NonEmptyList.of(checkApiCompatibilityEndpoint)

}
