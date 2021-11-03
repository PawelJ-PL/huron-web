package com.github.huronapp.api.http

import com.github.huronapp.api.auth.authentication.{AuthenticatedUser, AuthenticationInputs}
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import org.http4s.HttpRoutes
import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.typelevel.ParamSubtract
import sttp.tapir.ztapir._
import zio.ZIO

object EndpointSyntax {

  implicit class ZendpointOps[I, E, O](zendpoint: Endpoint[I, E, O, ZioStreams with capabilities.WebSockets]) {

    def toRoutes(
      logic: I => ZIO[Any, E, O]
    )(
      implicit serverOptions: Http4sServerOptions[RouteEffect, RouteEffect]
    ): HttpRoutes[RouteEffect] =
      ZHttp4sServerInterpreter[Any](serverOptions).from[I, E, O](zendpoint)(logic).toRoutes

    def toAuthenticatedRoutes[IR](
      auth: AuthenticationInputs => ZIO[Any, E, AuthenticatedUser]
    )(
      logic: ((AuthenticatedUser, IR)) => ZIO[Any, E, O]
    )(
      implicit serverOptions: Http4sServerOptions[RouteEffect, RouteEffect],
      iMinusT: ParamSubtract.Aux[I, AuthenticationInputs, IR]
    ): HttpRoutes[RouteEffect] =
      ZHttp4sServerInterpreter[Any](serverOptions)
        .from(zendpoint.zServerLogicPart[Any, AuthenticationInputs, IR, AuthenticatedUser](auth).andThen[Any](logic))
        .toRoutes

    def toAuthenticatedRoutes_[IR](
      auth: AuthenticationInputs => ZIO[Any, E, AuthenticatedUser]
    )(
      logic: IR => ZIO[Any, E, O]
    )(
      implicit serverOptions: Http4sServerOptions[RouteEffect, RouteEffect],
      iMinusT: ParamSubtract.Aux[I, AuthenticationInputs, IR]
    ): HttpRoutes[RouteEffect] =
      ZHttp4sServerInterpreter[Any](serverOptions)
        .from(
          zendpoint.zServerLogicPart[Any, AuthenticationInputs, IR, AuthenticatedUser](auth).andThen[Any] { case (_, rest) => logic(rest) }
        )
        .toRoutes

  }

}
