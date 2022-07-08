package com.github.huronapp.api.auth.authorization

import cats.syntax.eq._
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.domain.collections.{CollectionId, CollectionPermission, CollectionsRepository}
import com.github.huronapp.api.domain.collections.CollectionsRepository.CollectionsRepository
import com.github.huronapp.api.domain.users.UserId
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
            case operation @ GetCollectionDetails(subject, collectionId)             =>
              db.transactionOrDie(for {
                hasCollection <- collectionsRepo.isCollectionAssignedToUser(collectionId, subject.userId).orDie
                _             <- ZIO.cond(hasCollection, (), OperationNotPermitted(operation))
              } yield ())

            case operation @ InviteCollectionMember(subject, collectionId, _)        =>
              validateCollectionPermission(subject, collectionId, Set(CollectionPermission.ManageCollection), operation)

            case operation @ GetCollectionMembers(subject, collectionId)             =>
              validateCollectionPermission(subject, collectionId, Set(CollectionPermission.ManageCollection), operation)

            case operation @ SetCollectionPermissions(subject, collectionId, _)      =>
              validateCollectionPermission(subject, collectionId, Set(CollectionPermission.ManageCollection), operation)

            case operation @ RemoveCollectionMember(subject, collectionId, memberId) =>
              if (subject.userId === memberId.id)
                db.transactionOrDie(for {
                  isMember <- collectionsRepo.isMemberOf(UserId(subject.userId), collectionId).orDie
                  _        <- ZIO.cond(isMember, (), OperationNotPermitted(operation))
                } yield ())
              else
                validateCollectionPermission(subject, collectionId, Set(CollectionPermission.ManageCollection), operation)

            case operation @ GetKeyPair(subject, keyPairOwner)                       =>
              ZIO.cond(subject.userId === keyPairOwner.id, (), OperationNotPermitted(operation))

            case operation @ SetEncryptionKey(subject, collectionId, userId)         =>
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

            case operation @ GetEncryptionKey(subject, collectionId)                 =>
              db.transactionOrDie(
                collectionsRepo
                  .isCollectionAssignedToUser(collectionId, subject.userId)
                  .orDie
                  .flatMap(isAssigned => ZIO.cond(isAssigned, (), OperationNotPermitted(operation)))
              )

            case operation @ DeleteCollection(subject, collectionId)                 =>
              db.transactionOrDie(
                for {
                  collectionDetails <-
                    collectionsRepo.getCollectionDetails(collectionId.id).orDie.someOrFail(OperationNotPermitted(operation))
                  _                 <- ZIO.cond(collectionDetails.owner === subject.userId, (), OperationNotPermitted(operation))
                } yield ()
              )

            case operation @ CreateFile(subject, collectionId)                       =>
              validateCollectionPermission(
                subject,
                collectionId,
                Set(CollectionPermission.CreateFile, CollectionPermission.ReadFileMetadata),
                operation
              )

            case operation @ ReadMetadata(subject, collectionId)                     =>
              validateCollectionPermission(subject, collectionId, Set(CollectionPermission.ReadFileMetadata), operation)

            case operation @ ReadContent(subject, collectionId)                      =>
              validateCollectionPermission(
                subject,
                collectionId,
                Set(CollectionPermission.ReadFile, CollectionPermission.ReadFileMetadata),
                operation
              )

            case operation @ ModifyFile(subject, collectionId)                       =>
              validateCollectionPermission(
                subject,
                collectionId,
                Set(CollectionPermission.ModifyFile, CollectionPermission.ReadFileMetadata),
                operation
              )

            case operation @ DeleteFile(subject, collectionId)                       =>
              validateCollectionPermission(
                subject,
                collectionId,
                Set(CollectionPermission.ModifyFile, CollectionPermission.ReadFileMetadata),
                operation
              )

            case operation @ ListMemberPermissions(subject, collectionId, memberId)  =>
              validateCollectionPermission(subject, collectionId, Set(CollectionPermission.ManageCollection), operation)
                .unless(subject.userId === memberId.id)
          }

        private def validateCollectionPermission(
          subject: Subject,
          collectionId: CollectionId,
          requiredPermission: Set[CollectionPermission],
          operation: AuthorizedOperation
        ): ZIO[Any, OperationNotPermitted, Unit] =
          db.transactionOrDie(
            for {
              permissions <- collectionsRepo.getUserPermissionsFor(collectionId.id, subject.userId).orDie
              _           <- ZIO.cond(requiredPermission.subsetOf(permissions.toSet), (), OperationNotPermitted(operation))
            } yield ()
          )

      }
    )

}
