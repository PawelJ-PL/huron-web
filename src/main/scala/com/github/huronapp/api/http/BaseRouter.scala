package com.github.huronapp.api.http

import com.github.huronapp.api.http.BaseRouter.RouteEffect
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.http4s.Http4sServerOptions.Log
import sttp.tapir.server.interceptor.exception.DefaultExceptionHandler
import zio.ZIO
import zio.clock.Clock
import zio.interop.catz._

trait BaseRouter {

  implicit val serverOpts: Http4sServerOptions[RouteEffect, RouteEffect] =
    Http4sServerOptions.customInterceptors[RouteEffect, RouteEffect](
      decodeFailureHandler = TapirHandlers.handler,
      exceptionHandler = Some(DefaultExceptionHandler),
      serverLog = Some(Log.defaultServerLog[RouteEffect])
    )

}

object BaseRouter {

  type RouteEffect[A] = ZIO[Clock, Throwable, A]

}
