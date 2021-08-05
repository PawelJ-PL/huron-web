package com.github.huronapp.api.domain.collections

import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.auth.authorization.{AuthorizationKernel, GetCollectionDetails, OperationNotPermitted}
import com.github.huronapp.api.constants.{Collections, MiscConstants, Users}
import com.github.huronapp.api.domain.collections.dto.NewCollectionReq
import com.github.huronapp.api.domain.collections.dto.fields.{CollectionName, EncryptedCollectionKey}
import com.github.huronapp.api.testdoubles.{CollectionsRepoFake, RandomUtilsStub}
import io.github.gaelrenoux.tranzactio.doobie.Database
import zio.{Has, Ref, ZLayer}
import zio.logging.slf4j.Slf4jLogger
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec}
import zio.test.assert
import zio.test.Assertion.{equalTo, hasSameElements, isLeft}

object CollectionsServiceSpec extends DefaultRunnableSpec with Collections with Users with MiscConstants {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Collections service suite")(
      getCollections,
      getOnlyAcceptedCollections,
      getCollectionDetails,
      getCollectionDetailsNotPermitted,
      createCollection
    )

  private def createService(
    collectionsRepoState: Ref[CollectionsRepoFake.CollectionsRepoState]
  ): ZLayer[TestEnvironment, Nothing, Has[CollectionsService.Service]] = {
    val collectionsRepo = CollectionsRepoFake.create(collectionsRepoState)
    val logger = Slf4jLogger.make((_, str) => str)
    Database.none ++ collectionsRepo ++ (Database.none ++ collectionsRepo >>> AuthorizationKernel.live) ++ RandomUtilsStub.create ++ logger >>> CollectionsService.live
  }

  val crateCollectionDto: NewCollectionReq =
    NewCollectionReq(CollectionName(ExampleCollectionName), EncryptedCollectionKey(ExampleEncryptionKey))

  private val getCollections = testM("should list all collections of user") {
    val collection1 = ExampleCollection
    val collection2 = ExampleCollection.copy(id = ExampleFuuid1)
    val collection3 = ExampleCollection.copy(id = ExampleFuuid2)
    val collection4 = ExampleCollection.copy(id = ExampleFuuid3)
    val collection5 = ExampleCollection.copy(id = ExampleFuuid4)

    val userCollection1 = CollectionsRepoFake.UserCollection(collection1.id, ExampleUserId, accepted = true)
    val userCollection2 = CollectionsRepoFake.UserCollection(collection2.id, ExampleFuuid5, accepted = true)
    val userCollection3 = CollectionsRepoFake.UserCollection(collection3.id, ExampleUserId, accepted = false)
    val userCollection4 = CollectionsRepoFake.UserCollection(collection4.id, ExampleFuuid5, accepted = false)
    val userCollection5 = CollectionsRepoFake.UserCollection(collection5.id, ExampleUserId, accepted = true)

    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(collection1, collection2, collection3, collection4, collection5),
      userCollections = Set(userCollection1, userCollection2, userCollection3, userCollection4, userCollection5)
    )
    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      result          <- CollectionsService.getAllCollectionsOfUser(ExampleUserId, onlyAccepted = false).provideLayer(createService(collectionsRepo))
    } yield assert(result)(hasSameElements(Set(collection1, collection3, collection5)))
  }

  private val getOnlyAcceptedCollections = testM("should list accepted collections of user") {
    val collection1 = ExampleCollection
    val collection2 = ExampleCollection.copy(id = ExampleFuuid1)
    val collection3 = ExampleCollection.copy(id = ExampleFuuid2)
    val collection4 = ExampleCollection.copy(id = ExampleFuuid3)
    val collection5 = ExampleCollection.copy(id = ExampleFuuid4)

    val userCollection1 = CollectionsRepoFake.UserCollection(collection1.id, ExampleUserId, accepted = true)
    val userCollection2 = CollectionsRepoFake.UserCollection(collection2.id, ExampleFuuid5, accepted = true)
    val userCollection3 = CollectionsRepoFake.UserCollection(collection3.id, ExampleUserId, accepted = false)
    val userCollection4 = CollectionsRepoFake.UserCollection(collection4.id, ExampleFuuid5, accepted = false)
    val userCollection5 = CollectionsRepoFake.UserCollection(collection5.id, ExampleUserId, accepted = true)

    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(collection1, collection2, collection3, collection4, collection5),
      userCollections = Set(userCollection1, userCollection2, userCollection3, userCollection4, userCollection5)
    )
    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      result          <- CollectionsService.getAllCollectionsOfUser(ExampleUserId, onlyAccepted = true).provideLayer(createService(collectionsRepo))
    } yield assert(result)(hasSameElements(Set(collection1, collection5)))
  }

  private val getCollectionDetails = testM("should read collection details") {
    val userCollection = CollectionsRepoFake.UserCollection(ExampleCollection.id, ExampleUserId, accepted = true)

    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(userCollection)
    )
    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      result          <- CollectionsService.getCollectionDetailsAs(ExampleUserId, ExampleCollectionId).provideLayer(createService(collectionsRepo))
    } yield assert(result)(equalTo(ExampleCollection))
  }

  private val getCollectionDetailsNotPermitted = testM("should read collection if user has no rights") {
    val userCollection = CollectionsRepoFake.UserCollection(ExampleCollection.id, ExampleFuuid1, accepted = true)

    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(userCollection)
    )
    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      result          <-
        CollectionsService.getCollectionDetailsAs(ExampleUserId, ExampleCollectionId).provideLayer(createService(collectionsRepo)).either
    } yield assert(result)(
      isLeft(equalTo(AuthorizationError(OperationNotPermitted(GetCollectionDetails(Subject(ExampleUserId), ExampleCollectionId)))))
    )
  }

  private val createCollection = testM("should create new collection") {
    val expectedCollection = Collection(FirstRandomFuuid, ExampleCollectionName, SecondRandomFuuid)

    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState()
    for {
      collectionsRepo          <- Ref.make(initCollectionsRepoState)
      result                   <- CollectionsService.createCollectionAs(ExampleUserId, crateCollectionDto).provideLayer(createService(collectionsRepo))
      finalCollectionRepoState <- collectionsRepo.get
    } yield assert(result)(equalTo(expectedCollection)) &&
      assert(finalCollectionRepoState.collections)(hasSameElements(Set(expectedCollection))) &&
      assert(finalCollectionRepoState.userCollections)(
        hasSameElements(Set(CollectionsRepoFake.UserCollection(FirstRandomFuuid, ExampleUserId, accepted = true)))
      ) &&
      assert(finalCollectionRepoState.permissions)(
        hasSameElements(
          Set(
            CollectionsRepoFake.PermissionEntry(FirstRandomFuuid, ExampleUserId, CollectionPermission.ManageCollection),
            CollectionsRepoFake.PermissionEntry(FirstRandomFuuid, ExampleUserId, CollectionPermission.WriteFile),
            CollectionsRepoFake.PermissionEntry(FirstRandomFuuid, ExampleUserId, CollectionPermission.ReadFile),
            CollectionsRepoFake.PermissionEntry(FirstRandomFuuid, ExampleUserId, CollectionPermission.ReadFileMetadata)
          )
        )
      )
  }

}
