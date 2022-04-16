package com.github.huronapp.api.http

import com.github.huronapp.api.http.BaseRouter.RouteEffect
import sttp.tapir.server.http4s.Http4sServerOptions
import zio.ZIO
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._

trait BaseRouter {

  implicit val serverOpts: Http4sServerOptions[RouteEffect, RouteEffect] =
    Http4sServerOptions
      .customInterceptors[RouteEffect, RouteEffect]
      .decodeFailureHandler(TapirHandlers.handler)
      .options

}

object BaseRouter {

  type RouteEffect[A] = ZIO[Any with Clock with Blocking, Throwable, A]

}
