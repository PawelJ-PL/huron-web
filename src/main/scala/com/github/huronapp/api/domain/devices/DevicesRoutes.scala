package com.github.huronapp.api.domain.devices

import com.github.huronapp.api.config.MobileAppConfig
import com.github.huronapp.api.utils.SemverWrapper._
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import com.github.huronapp.api.http.EndpointSyntax._
import com.github.huronapp.api.http.{BaseRouter, ErrorResponse}
import org.http4s.HttpRoutes
import zio.{Has, URIO, ZIO, ZLayer}

object DevicesRoutes {

  type DevicesRoutes = Has[DevicesRoutes.Service]

  trait Service {

    val routes: HttpRoutes[RouteEffect]

  }

  val routes: URIO[DevicesRoutes, HttpRoutes[RouteEffect]] = ZIO.access[DevicesRoutes](_.get.routes)

  val live: ZLayer[Has[MobileAppConfig], Nothing, DevicesRoutes] = ZLayer.fromService[MobileAppConfig, DevicesRoutes.Service](conf =>
    new Service with BaseRouter {

      private val checkCompatibilityRoute = DevicesEndpoints
        .checkApiCompatibilityEndpoint
        .toRoutes(dto =>
          ZIO.fail(ErrorResponse.PreconditionFailed("Incompatible version", None)).unless(dto.appVersion >= conf.lastSupportedVersion)
        )

      override val routes: HttpRoutes[RouteEffect] = checkCompatibilityRoute

    }
  )

}
