package com.github.huronapp.api.domain.collections

import cats.syntax.semigroupk._
import com.github.huronapp.api.auth.authentication.HttpAuthentication
import com.github.huronapp.api.auth.authentication.HttpAuthentication.HttpAuthentication
import com.github.huronapp.api.domain.collections.CollectionsService.CollectionsService
import com.github.huronapp.api.domain.collections.dto.{CollectionData, NewCollectionReq}
import com.github.huronapp.api.http.BaseRouter
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import com.github.huronapp.api.http.EndpointSyntax._
import io.chrisdavenport.fuuid.FUUID
import io.scalaland.chimney.dsl._
import org.http4s.HttpRoutes
import zio.logging.{Logger, Logging}
import zio.{Has, URIO, ZIO, ZLayer}
import zio.interop.catz._

object CollectionsRoutes {

  type CollectionsRoutes = Has[CollectionsRoutes.Service]

  trait Service {

    val routes: HttpRoutes[RouteEffect]

  }

  val routes: URIO[CollectionsRoutes, HttpRoutes[RouteEffect]] = ZIO.access[CollectionsRoutes](_.get.routes)

  val live: ZLayer[CollectionsService with Logging with HttpAuthentication, Nothing, Has[Service]] =
    ZLayer.fromServices[CollectionsService.Service, Logger[String], HttpAuthentication.Service, CollectionsRoutes.Service](
      (collectionService, logger, auth) =>
        new Service with BaseRouter {

          private val listCollectionsRoute: HttpRoutes[RouteEffect] =
            CollectionsEndpoints.listCollectionsEndpoint.toAuthenticatedRoutes[Option[Boolean]](auth.asUser) {
              case (user, onlyAccepted) =>
                collectionService
                  .getAllCollectionsOfUser(user.userId, onlyAccepted.getOrElse(false))
                  .map(_.transformInto[List[CollectionData]])
            }

          private val getCollectionDetailsRoute: HttpRoutes[RouteEffect] =
            CollectionsEndpoints.getCollectionDetailsEndpoint.toAuthenticatedRoutes[FUUID](auth.asUser) {
              case (user, collectionId) =>
                collectionService
                  .getCollectionDetailsAs(user.userId, collectionId)
                  .map(_.transformInto[CollectionData])
                  .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.getCollectionDetailsError(error)))
            }

          private val createCollection: HttpRoutes[RouteEffect] =
            CollectionsEndpoints.createCollectionEndpoint.toAuthenticatedRoutes[NewCollectionReq](auth.asUser) {
              case (user, dto) => collectionService.createCollectionAs(user.userId, dto).map(_.transformInto[CollectionData])
            }

          override val routes: HttpRoutes[RouteEffect] = listCollectionsRoute <+> getCollectionDetailsRoute <+> createCollection

        }
    )

}
