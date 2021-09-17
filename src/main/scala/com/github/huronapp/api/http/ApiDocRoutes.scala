package com.github.huronapp.api.http

import cats.data.NonEmptyList
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.SwaggerUI
import sttp.tapir.ztapir.{ZEndpoint, ZServerEndpoint}
import zio.{Has, URIO, ZIO, ZLayer}

object ApiDocRoutes {

  type ApiDocRoutes = Has[ApiDocRoutes.Service]

  trait Service {

    val routes: HttpRoutes[RouteEffect]

  }

  val routes: URIO[ApiDocRoutes, HttpRoutes[RouteEffect]] = ZIO.access[ApiDocRoutes](_.get.routes)

  def live(endpoints: NonEmptyList[ZEndpoint[_, _, _]]): ZLayer[Any, Nothing, ApiDocRoutes] =
    ZLayer.succeed(new Service with Http4sDsl[RouteEffect] {

      private val doc = OpenAPIDocsInterpreter().toOpenAPI(endpoints.toList, "Huron App", "").toYaml

      private val swaggerEndpoint: List[ZServerEndpoint[Any, _, _, _]] = SwaggerUI(doc, yamlName = "api-doc.yaml")

      private val swaggerRoutes = ZHttp4sServerInterpreter[Any]().from(swaggerEndpoint).toRoutes

      override val routes: HttpRoutes[RouteEffect] = swaggerRoutes

    })

}
