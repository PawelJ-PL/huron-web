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
import kamon.http4s.middleware.server.{KamonSupport => KamonServerMiddleware}
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import zio.{Runtime, ZEnv, ZManaged}
import zio.interop.catz._
import zio.interop.catz.implicits._

object HttpServer {

  private val httpRoutes = for {
    swaggerRoutes     <- ApiDocRoutes.routes
    devicesRoutes     <- DevicesRoutes.routes
    usersRoutes       <- UsersRoutes.routes
    collectionsRoutes <- CollectionsRoutes.routes
    filesRoutes       <- FilesRoutes.routes
  } yield swaggerRoutes <+> devicesRoutes <+> usersRoutes <+> collectionsRoutes <+> filesRoutes

  val create: ZManaged[ZEnv with AppEnvironment, Throwable, Server[RouteEffect]] = for {
    implicit0(runtime: Runtime[ZEnv]) <- ZManaged.runtime[ZEnv]
    appConfig                         <- ZManaged.service[AppConfig]
    routes                            <- httpRoutes.toManaged_
    tracedRoutes = KamonServerMiddleware(routes, appConfig.server.bindAddress, appConfig.server.port)
    server                            <- BlazeServerBuilder[RouteEffect](runtime.platform.executor.asEC)
                                           .bindHttp(appConfig.server.port, appConfig.server.bindAddress)
                                           .withHttpApp(tracedRoutes.orNotFound)
                                           .resource
                                           .toManagedZIO
  } yield server

}
