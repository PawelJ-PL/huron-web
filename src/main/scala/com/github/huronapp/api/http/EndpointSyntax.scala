package com.github.huronapp.api.http

import com.github.huronapp.api.auth.authentication.AuthenticatedUser
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import org.http4s.HttpRoutes
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir._
import zio.ZIO

object EndpointSyntax {

  implicit class ZendpointOps[S, I, E, O](zendpoint: Endpoint[S, I, E, O, ZioStreams]) {

    def toRoutes(
      logic: I => ZIO[Any, E, O]
    )(
      implicit serverOptions: Http4sServerOptions[RouteEffect],
      aIsUnit: S =:= Unit
    ): HttpRoutes[RouteEffect] = {
      val zserver: ZServerEndpoint[Any, ZioStreams] = zendpoint.zServerLogic(logic)
      ZHttp4sServerInterpreter[Any](serverOptions).from(zserver).toRoutes
    }

    def toAuthenticatedRoutes(
      auth: S => ZIO[Any, E, AuthenticatedUser]
    )(
      logic: AuthenticatedUser => I => ZIO[Any, E, O]
    )(
      implicit serverOptions: Http4sServerOptions[RouteEffect]
    ): HttpRoutes[RouteEffect] = {
      val partialWithUserServerEndpoint = zendpoint.zServerSecurityLogic[Any, AuthenticatedUser](auth)
      val serverEndpoint: ZServerEndpoint[Any, ZioStreams] = partialWithUserServerEndpoint.serverLogic[Any](logic)
      ZHttp4sServerInterpreter[Any](serverOptions).from(serverEndpoint).toRoutes
    }

    def toAuthenticatedRoutes_(
      auth: S => ZIO[Any, E, AuthenticatedUser]
    )(
      logic: I => ZIO[Any, E, O]
    )(
      implicit serverOptions: Http4sServerOptions[RouteEffect]
    ): HttpRoutes[RouteEffect] = {
      val partialWithUserServerEndpoint = zendpoint.zServerSecurityLogic[Any, AuthenticatedUser](auth)
      val serverEndpoint: ZServerEndpoint[Any, ZioStreams] = partialWithUserServerEndpoint.serverLogic[Any](_ => input => logic(input))
      ZHttp4sServerInterpreter[Any](serverOptions).from(serverEndpoint).toRoutes
    }

  }

}
