package com.github.huronapp.api.http

import cats.data.NonEmptyList
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.Endpoint
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.{SwaggerUI, SwaggerUIOptions}
import sttp.tapir.ztapir.ZServerEndpoint
import zio.{Has, URIO, ZIO, ZLayer}

object ApiDocRoutes {

  type ApiDocRoutes = Has[ApiDocRoutes.Service]

  trait Service {

    val routes: HttpRoutes[RouteEffect]

  }

  val routes: URIO[ApiDocRoutes, HttpRoutes[RouteEffect]] = ZIO.access[ApiDocRoutes](_.get.routes)

  def live(endpoints: NonEmptyList[Endpoint[_, _, _, _, Any]]): ZLayer[Any, Nothing, ApiDocRoutes] =
    ZLayer.succeed(new Service with Http4sDsl[RouteEffect] {

      private val doc = OpenAPIDocsInterpreter().toOpenAPI(endpoints.toList, "Huron App", "").toYaml

      private val swaggerEndpoint: List[ZServerEndpoint[Any, ZioStreams]] =
        SwaggerUI(doc, options = SwaggerUIOptions.default.copy(yamlName = "api-doc.yaml"))

      private val swaggerRoutes = ZHttp4sServerInterpreter[Any]().from(swaggerEndpoint).toRoutes

      override val routes: HttpRoutes[RouteEffect] = swaggerRoutes

    })

}
