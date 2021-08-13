package com.github.huronapp.api.domain.collections

import cats.syntax.show._
import cats.syntax.semigroupk._
import com.github.huronapp.api.auth.authentication.HttpAuthentication
import com.github.huronapp.api.auth.authentication.HttpAuthentication.HttpAuthentication
import com.github.huronapp.api.domain.collections.CollectionsService.CollectionsService
import com.github.huronapp.api.domain.collections.dto.{CollectionData, EncryptionKeyData, NewCollectionReq}
import com.github.huronapp.api.http.{BaseRouter, ErrorResponse}
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

          private val createCollectionRoute: HttpRoutes[RouteEffect] =
            CollectionsEndpoints.createCollectionEndpoint.toAuthenticatedRoutes[NewCollectionReq](auth.asUser) {
              case (user, dto) => collectionService.createCollectionAs(user.userId, dto).map(_.transformInto[CollectionData])
            }

          private val getCollectionKeyRoute =
            CollectionsEndpoints.getSingleCollectionKeyEndpoint.toAuthenticatedRoutes[FUUID](auth.asUser) {
              case (user, collectionId) =>
                collectionService
                  .getEncryptionKeyAs(user.userId, collectionId)
                  .map(_.transformInto[Option[EncryptionKeyData]])
                  .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.getEncryptionKeyError(error)))
                  .someOrFail(ErrorResponse.NotFound(show"Encryption key not found for collection $collectionId"))
            }

          private val getAllCollectionsKeysRoute =
            CollectionsEndpoints.getEncryptionKeysForAllCollectionsEndpoint.toAuthenticatedRoutes[Unit](auth.asUser) {
              case (user, _) =>
                collectionService.getEncryptionKeysForAllCollectionsOfUser(user.userId).map(_.transformInto[List[EncryptionKeyData]])
            }

          override val routes: HttpRoutes[RouteEffect] =
            getAllCollectionsKeysRoute <+> listCollectionsRoute <+> getCollectionDetailsRoute <+> createCollectionRoute <+> getCollectionKeyRoute

        }
    )

}
