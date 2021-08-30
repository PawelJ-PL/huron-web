package com.github.huronapp.api.domain.collections

import cats.syntax.show._
import com.github.huronapp.api.auth.authorization.{AuthorizationKernel, GetCollectionDetails, GetEncryptionKey}
import com.github.huronapp.api.auth.authorization.AuthorizationKernel.AuthorizationKernel
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.domain.collections.CollectionsRepository.CollectionsRepository
import com.github.huronapp.api.domain.collections.dto.NewCollectionReq
import com.github.huronapp.api.utils.RandomUtils
import com.github.huronapp.api.utils.RandomUtils.RandomUtils
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.doobie.Database
import zio.logging.{Logger, Logging}
import zio.{Has, ZIO, ZLayer}
import zio.macros.accessible

@accessible
object CollectionsService {

  type CollectionsService = Has[CollectionsService.Service]

  trait Service {

    def getAllCollectionsOfUser(userId: FUUID, onlyAccepted: Boolean): ZIO[Any, Nothing, List[Collection]]

    def getCollectionDetailsAs(userId: FUUID, collectionId: FUUID): ZIO[Any, GetCollectionDetailsError, Collection]

    def createCollectionAs(userId: FUUID, dto: NewCollectionReq): ZIO[Any, Nothing, Collection]

    def getEncryptionKeyAs(userId: FUUID, collectionId: FUUID): ZIO[Any, GetEncryptionKeyError, Option[EncryptionKey]]

    def getEncryptionKeysForAllCollectionsOfUser(userId: FUUID): ZIO[Any, Nothing, List[EncryptionKey]]

  }

  val live
    : ZLayer[Database.Database with CollectionsRepository with AuthorizationKernel with RandomUtils with Logging, Nothing, Has[Service]] =
    ZLayer.fromServices[
      Database.Service,
      CollectionsRepository.Service,
      AuthorizationKernel.Service,
      RandomUtils.Service,
      Logger[String],
      CollectionsService.Service
    ] { (db, collectionsRepo, authKernel, random, logger) =>
      new Service {
        override def getAllCollectionsOfUser(userId: FUUID, onlyAccepted: Boolean): ZIO[Any, Nothing, List[Collection]] =
          db.transactionOrDie(collectionsRepo.listUsersCollections(userId, onlyAccepted).orDie)

        override def getCollectionDetailsAs(userId: FUUID, collectionId: FUUID): ZIO[Any, GetCollectionDetailsError, Collection] =
          db.transactionOrDie(
            for {
              _    <- authKernel.authorizeOperation(GetCollectionDetails(Subject(userId), collectionId)).mapError(AuthorizationError)
              data <- collectionsRepo.getCollectionDetails(collectionId).orDie.someOrFail(CollectionNotFound(collectionId))
            } yield data
          )

        override def createCollectionAs(userId: FUUID, dto: NewCollectionReq): ZIO[Any, Nothing, Collection] =
          db.transactionOrDie(
            for {
              collectionId <- random.randomFuuid
              keyVersion   <- random.randomFuuid
              collection = Collection(collectionId, dto.name.value, keyVersion, userId)
              saved        <- collectionsRepo.createCollection(collection, userId, dto.encryptedKey.value).orDie
              _            <- logger.info(show"User $userId created collection $collectionId")
            } yield saved
          )

        override def getEncryptionKeyAs(userId: FUUID, collectionId: FUUID): ZIO[Any, GetEncryptionKeyError, Option[EncryptionKey]] =
          db.transactionOrDie(
            for {
              _             <- authKernel.authorizeOperation(GetEncryptionKey(Subject(userId), collectionId)).mapError(AuthorizationError)
              encryptionKey <- collectionsRepo.getEncryptedKeyFor(collectionId, userId).orDie
            } yield encryptionKey
          )

        override def getEncryptionKeysForAllCollectionsOfUser(userId: FUUID): ZIO[Any, Nothing, List[EncryptionKey]] =
          db.transactionOrDie(collectionsRepo.getAllCollectionKeysOfUser(userId).orDie)
      }
    }

}
