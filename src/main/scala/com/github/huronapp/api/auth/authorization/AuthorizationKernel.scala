package com.github.huronapp.api.auth.authorization

import cats.syntax.eq._
import com.github.huronapp.api.domain.collections.{CollectionPermission, CollectionsRepository}
import com.github.huronapp.api.domain.collections.CollectionsRepository.CollectionsRepository
import io.github.gaelrenoux.tranzactio.doobie.Database
import zio.{Has, ZIO, ZLayer}

object AuthorizationKernel {

  type AuthorizationKernel = Has[AuthorizationKernel.Service]

  trait Service {

    def authorizeOperation(operation: AuthorizedOperation): ZIO[Any, AuthorizationError, Unit]

  }

  def authorizeOperation(operation: AuthorizedOperation): ZIO[AuthorizationKernel, AuthorizationError, Unit] =
    ZIO.accessM[AuthorizationKernel](_.get.authorizeOperation(operation))

  val live: ZLayer[Database.Database with CollectionsRepository, Nothing, Has[Service]] =
    ZLayer.fromServices[Database.Service, CollectionsRepository.Service, AuthorizationKernel.Service]((db, collectionsRepo) =>
      new Service {

        override def authorizeOperation(operation: AuthorizedOperation): ZIO[Any, AuthorizationError, Unit] =
          operation match {
            case operation @ GetCollectionDetails(subject, collectionId)     =>
              db.transactionOrDie(for {
                hasCollection <- collectionsRepo.isCollectionAssignedToUser(collectionId, subject.userId).orDie
                _             <- ZIO.cond(hasCollection, (), OperationNotPermitted(operation))
              } yield ())

            case operation @ SetEncryptionKey(subject, collectionId, userId) =>
              db.transactionOrDie(
                for {
                  isCollectionAssignedToUser <- collectionsRepo.isCollectionAssignedToUser(collectionId, userId).orDie
                  _                          <- ZIO.cond(isCollectionAssignedToUser, (), OperationNotPermitted(operation))
                  canSetKey                  <- if (subject.userId === userId) ZIO.succeed(true)
                                                else
                                                  collectionsRepo
                                                    .getUserPermissionsFor(collectionId, subject.userId)
                                                    .orDie
                                                    .map(_.contains(CollectionPermission.ManageCollection))
                  _                          <- ZIO.cond(canSetKey, (), OperationNotPermitted(operation))
                } yield ()
              )

            case operation @ GetEncryptionKey(subject, collectionId)         =>
              db.transactionOrDie(
                collectionsRepo
                  .isCollectionAssignedToUser(collectionId, subject.userId)
                  .orDie
                  .flatMap(isAssigned => ZIO.cond(isAssigned, (), OperationNotPermitted(operation)))
              )
          }

      }
    )

}
