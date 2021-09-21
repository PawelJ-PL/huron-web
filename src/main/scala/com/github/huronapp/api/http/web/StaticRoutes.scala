package com.github.huronapp.api.http.web

import cats.syntax.semigroupk._
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Response, StaticFile}
import org.http4s.server.staticcontent.resourceServiceBuilder
import zio.blocking.Blocking
import zio.{Has, URIO, ZIO, ZLayer}
import zio.interop.catz._

object StaticRoutes {

  type StaticRoutes = Has[StaticRoutes.Service]

  trait Service {

    val routes: HttpRoutes[RouteEffect]

  }

  val routes: URIO[StaticRoutes, HttpRoutes[RouteEffect]] = ZIO.access[StaticRoutes](_.get.routes)

  val live: ZLayer[Blocking, Nothing, StaticRoutes] = ZLayer.succeed(
    new Service with Http4sDsl[RouteEffect] {

      override val routes: HttpRoutes[RouteEffect] = {
        val staticFileRoutes: HttpRoutes[RouteEffect] =
          resourceServiceBuilder[RouteEffect]("/static/static").withPathPrefix("/static").toRoutes

        def rootDirResource(path: String): RouteEffect[Response[RouteEffect]] =
          StaticFile.fromResource[RouteEffect]("/static" + path).getOrElseF(NotFound())

        val indexResource: RouteEffect[Response[RouteEffect]] =
          StaticFile.fromResource[RouteEffect]("/static/index.html").getOrElseF[Response[RouteEffect]](NotFound())

        val webRoutes = HttpRoutes.of[RouteEffect] {
          case GET -> Root / "asset-manifest.json" => rootDirResource("/asset-manifest.json")
          case GET -> Root / "favicon.ico"         => rootDirResource("/favicon.ico")
          case GET -> Root / "logo192.png"         => rootDirResource("/logo192.png")
          case GET -> Root / "logo512.png"         => rootDirResource("/logo512.png")
          case GET -> Root / "manifest.json"       => rootDirResource("/manifest.json")
          case GET -> Root / "robots.txt"          => rootDirResource("/robots.txt")
          case GET -> Root                         => indexResource
          case GET -> _ /: _                       => indexResource
        }

        staticFileRoutes <+> webRoutes
      }

    }
  )

}
