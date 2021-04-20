package com.github.huronapp.api.http

import cats.data.NonEmptyList
import cats.syntax.semigroupk._
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.implicits._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import sttp.tapir.ztapir.ZEndpoint
import zio.{Has, URIO, ZIO, ZLayer}
import zio.interop.catz._

object ApiDocRoutes {

  type ApiDocRoutes = Has[ApiDocRoutes.Service]

  trait Service {

    val routes: HttpRoutes[RouteEffect]

  }

  val routes: URIO[ApiDocRoutes, HttpRoutes[RouteEffect]] = ZIO.access[ApiDocRoutes](_.get.routes)

  def live(endpoints: NonEmptyList[ZEndpoint[_, _, _]]): ZLayer[Any, Nothing, ApiDocRoutes] =
    ZLayer.succeed(new Service with Http4sDsl[RouteEffect] {

      private val doc = OpenAPIDocsInterpreter.toOpenAPI(endpoints.toList, "Huron App", "")

      private val swaggerRoutes = new SwaggerHttp4s(
        doc.toYaml,
        yamlName = "api-doc.yaml",
        redirectQuery = Map("defaultModelsExpandDepth" -> Seq("0"), "docExpansion" -> Seq("none"))
      ).routes[RouteEffect]

      private val slashRedirectRoute = HttpRoutes.of[RouteEffect] {
        case GET -> Root / "docs" / "" => PermanentRedirect(Location(uri"/docs"))
      }

      override val routes: HttpRoutes[RouteEffect] = slashRedirectRoute <+> swaggerRoutes

    })

}
