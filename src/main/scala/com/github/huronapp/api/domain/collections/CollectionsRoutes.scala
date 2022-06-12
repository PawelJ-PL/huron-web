package com.github.huronapp.api.domain.collections

import cats.syntax.show._
import cats.syntax.semigroupk._
import com.github.huronapp.api.auth.authentication.HttpAuthentication
import com.github.huronapp.api.auth.authentication.HttpAuthentication.HttpAuthentication
import com.github.huronapp.api.domain.collections.CollectionsService.CollectionsService
import com.github.huronapp.api.domain.collections.dto.{UserCollectionData, EncryptionKeyData}
import com.github.huronapp.api.domain.users.UserId
import com.github.huronapp.api.http.{BaseRouter, ErrorResponse}
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import com.github.huronapp.api.http.EndpointSyntax._
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
            CollectionsEndpoints
              .listCollectionsEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                onlyAccepted =>
                  collectionService
                    .getAllCollectionsOfUser(user.userId, onlyAccepted.getOrElse(false))
                    .map(_.map {
                      case (collection, isAccepted) =>
                        UserCollectionData(collection.id, collection.name, collection.encryptionKeyVersion, collection.owner, isAccepted)
                    })
              )

          private val getCollectionDetailsRoute: HttpRoutes[RouteEffect] =
            CollectionsEndpoints
              .getCollectionDetailsEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                collectionId =>
                  collectionService
                    .getCollectionDetailsAs(user.userId, collectionId)
                    .map {
                      case (collection, isAccepted) =>
                        UserCollectionData(collection.id, collection.name, collection.encryptionKeyVersion, collection.owner, isAccepted)
                    }
                    .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.getCollectionDetailsError(error)))
              )

          private val createCollectionRoute: HttpRoutes[RouteEffect] =
            CollectionsEndpoints
              .createCollectionEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                dto =>
                  collectionService
                    .createCollectionAs(user.userId, dto)
                    .map(_.into[UserCollectionData].withFieldConst(_.isAccepted, true).transform)
              )

          private val deleteCollectionRoute: HttpRoutes[RouteEffect] = CollectionsEndpoints
            .deleteCollectionEndpoint
            .toAuthenticatedRoutes(auth.asUser)(user =>
              collectionId =>
                collectionService
                  .deleteCollectionAs(UserId(user.userId), collectionId)
                  .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.deleteCollectionError(error)))
            )

          private val getCollectionKeyRoute =
            CollectionsEndpoints
              .getSingleCollectionKeyEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                collectionId =>
                  collectionService
                    .getEncryptionKeyAs(user.userId, collectionId)
                    .map(_.transformInto[Option[EncryptionKeyData]])
                    .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.getEncryptionKeyError(error)))
                    .someOrFail(ErrorResponse.NotFound(show"Encryption key not found for collection $collectionId"))
              )

          private val getAllCollectionsKeysRoute =
            CollectionsEndpoints
              .getEncryptionKeysForAllCollectionsEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                _ => collectionService.getEncryptionKeysForAllCollectionsOfUser(user.userId).map(_.transformInto[List[EncryptionKeyData]])
              )

          private val inviterMemberRoute = CollectionsEndpoints.inviteCollectionMemberEndpoint.toAuthenticatedRoutes(auth.asUser) { user =>
            {
              case (collectionId, memberId, dto) =>
                collectionService
                  .inviteMemberAs(UserId(user.userId), collectionId, memberId, dto)
                  .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.inviteMemberError(error)))
                  .unit
            }
          }

          private val acceptInvitationRoute = CollectionsEndpoints.acceptInvitationEndpoint.toAuthenticatedRoutes(auth.asUser) {
            user => collectionId =>
              collectionService
                .acceptInvitationAs(UserId(user.userId), collectionId)
                .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.acceptInvitationError(error)))
          }

          private val cancelInvitationAcceptanceRoute =
            CollectionsEndpoints.cancelInvitationAcceptanceEndpoint.toAuthenticatedRoutes(auth.asUser) { user => collectionId =>
              collectionService
                .cancelInvitationAcceptanceAs(UserId(user.userId), collectionId)
                .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.cancelInvitationAcceptanceError(error)))
            }

          private val getCollectionMembersRoute = CollectionsEndpoints
            .getCollectionMembersEndpoint
            .toAuthenticatedRoutes(auth.asUser) { user => collectionId =>
              collectionService
                .getCollectionMembersAs(UserId(user.userId), collectionId)
                .map(_.groupBy(_.userId).map { case (userId, member) => (userId.id -> member.flatMap(_.permissions)) })
                .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.getMembersError(error)))
            }

          private val listPermissionsRoute = CollectionsEndpoints.listPermissionsEndpoint.toAuthenticatedRoutes(auth.asUser) { user =>
            {
              case (collectionId, memberId) =>
                collectionService
                  .getMemberPermissionsAs(UserId(user.userId), collectionId, memberId)
                  .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.listMemberPermissionsError(error)))
            }
          }

          private val setPermissionsRoute = CollectionsEndpoints.setPermissionsEndpoint.toAuthenticatedRoutes(auth.asUser) { user =>
            {
              case (collectionId, memberId, newPermissions) =>
                collectionService
                  .setMemberPermissionsAs(UserId(user.userId), collectionId, memberId, newPermissions)
                  .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.setMemberPermissionsError(error)))
            }
          }

          private val removeMemberRoute = CollectionsEndpoints.removeCollectionMemberEndpoint.toAuthenticatedRoutes(auth.asUser) { user =>
            {
              case (collectionId, memberId) =>
                collectionService
                  .deleteMemberAs(UserId(user.userId), collectionId, memberId)
                  .flatMapError(error => logger.warn(error.logMessage).as(CollectionsErrorMapping.removeMemberError(error)))
            }
          }

          override val routes: HttpRoutes[RouteEffect] =
            getAllCollectionsKeysRoute <+>
              listCollectionsRoute <+>
              getCollectionDetailsRoute <+>
              createCollectionRoute <+>
              deleteCollectionRoute <+>
              getCollectionKeyRoute <+>
              inviterMemberRoute <+>
              acceptInvitationRoute <+>
              cancelInvitationAcceptanceRoute <+>
              getCollectionMembersRoute <+>
              listPermissionsRoute <+>
              setPermissionsRoute <+>
              removeMemberRoute

        }
    )

}
