package com.github.huronapp.api

import cats.syntax.semigroupk._
import com.github.huronapp.api.Environment.AppEnvironment
import com.github.huronapp.api.config.AppConfig
import com.github.huronapp.api.domain.collections.CollectionsRoutes
import com.github.huronapp.api.domain.devices.DevicesRoutes
import com.github.huronapp.api.domain.files.FilesRoutes
import com.github.huronapp.api.domain.users.UsersRoutes
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import com.github.huronapp.api.http.ApiDocRoutes
import com.github.huronapp.api.http.web.StaticRoutes
import kamon.http4s.middleware.server.{KamonSupport => KamonServerMiddleware}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import zio.{ZEnv, ZManaged}
import zio.interop.catz._

object HttpServer {

  private val httpRoutes = for {
    swaggerRoutes     <- ApiDocRoutes.routes
    devicesRoutes     <- DevicesRoutes.routes
    usersRoutes       <- UsersRoutes.routes
    collectionsRoutes <- CollectionsRoutes.routes
    filesRoutes       <- FilesRoutes.routes
    staticRoutes      <- StaticRoutes.routes // Must be the latest one
  } yield swaggerRoutes <+> devicesRoutes <+> usersRoutes <+> collectionsRoutes <+> filesRoutes <+> staticRoutes

  val create: ZManaged[ZEnv with AppEnvironment, Throwable, Server] = for {
    runtime   <- ZManaged.runtime[ZEnv]
    appConfig <- ZManaged.service[AppConfig]
    routes    <- httpRoutes.toManaged_
    tracedRoutes = KamonServerMiddleware(routes, appConfig.server.bindAddress, appConfig.server.port)
    server    <- BlazeServerBuilder[RouteEffect](runtime.platform.executor.asEC)
                   .bindHttp(appConfig.server.port, appConfig.server.bindAddress)
                   .withHttpApp(tracedRoutes.orNotFound)
                   .resource
                   .toManagedZIO
  } yield server

}
