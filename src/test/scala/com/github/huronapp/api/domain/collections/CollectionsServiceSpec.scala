package com.github.huronapp.api.domain.collections

import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.auth.authorization.{
  AuthorizationKernel,
  DeleteCollection,
  GetCollectionDetails,
  GetEncryptionKey,
  OperationNotPermitted
}
import com.github.huronapp.api.constants.{Collections, Files, MiscConstants, Users}
import com.github.huronapp.api.domain.collections.dto.NewCollectionReq
import com.github.huronapp.api.domain.collections.dto.fields.{CollectionName, EncryptedCollectionKey}
import com.github.huronapp.api.domain.files.StorageUnit
import com.github.huronapp.api.domain.users.UserId
import com.github.huronapp.api.testdoubles.{CollectionsRepoFake, FilesMetadataRepositoryFake, RandomUtilsStub, UsersRepoFake}
import io.github.gaelrenoux.tranzactio.doobie.Database
import zio.{Has, Ref, ZLayer}
import zio.logging.slf4j.Slf4jLogger
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert, assertTrue}
import zio.test.Assertion.{equalTo, hasSameElements, isLeft, isNone, isRight, isUnit}

object CollectionsServiceSpec extends DefaultRunnableSpec with Collections with Users with Files with MiscConstants {

  val userId: UserId = UserId(ExampleUserId)

  val collectionId: CollectionId = CollectionId(ExampleCollectionId)

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Collections service suite")(
      getCollections,
      getOnlyAcceptedCollections,
      getCollectionDetails,
      getCollectionDetailsNotPermitted,
      createCollection,
      readEncryptionKey,
      readEncryptionKeyNotFound,
      readEncryptionKeyNotPermitted,
      readAllEncryptionKeysOfUser,
      deleteCollection,
      deleteCollectionNonExisting,
      deleteCollectionByNonOwner,
      deleteNonEmptyCollection
    )

  private def createService(
    collectionsRepoState: Ref[CollectionsRepoFake.CollectionsRepoState],
    filesRepoState: Ref[List[StorageUnit]],
    usersRepoState: Ref[UsersRepoFake.UsersRepoState]
  ): ZLayer[TestEnvironment, Nothing, Has[CollectionsService.Service]] = {
    val collectionsRepo = CollectionsRepoFake.create(collectionsRepoState)
    val filesRepo = FilesMetadataRepositoryFake.create(filesRepoState)
    val usersRepo = UsersRepoFake.create(usersRepoState)
    val logger = Slf4jLogger.make((_, str) => str)
    Database.none ++ collectionsRepo ++ filesRepo ++ usersRepo ++ (Database.none ++ collectionsRepo >>> AuthorizationKernel.live) ++ RandomUtilsStub.create ++ logger >>> CollectionsService.live
  }

  val crateCollectionDto: NewCollectionReq =
    NewCollectionReq(CollectionName(ExampleCollectionName), EncryptedCollectionKey(ExampleEncryptionKeyValue))

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
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
      result          <- CollectionsService
                           .getAllCollectionsOfUser(ExampleUserId, onlyAccepted = false)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
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
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
      result          <- CollectionsService
                           .getAllCollectionsOfUser(ExampleUserId, onlyAccepted = true)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
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
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
      result          <- CollectionsService
                           .getCollectionDetailsAs(ExampleUserId, ExampleCollectionId)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
    } yield assertTrue(result == ExampleCollection)
  }

  private val getCollectionDetailsNotPermitted = testM("should not read collection if user has no rights") {
    val userCollection = CollectionsRepoFake.UserCollection(ExampleCollection.id, ExampleFuuid1, accepted = true)

    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection),
      userCollections = Set(userCollection)
    )
    for {
      collectionsRepo <- Ref.make(initCollectionsRepoState)
      filesRepo       <- Ref.make(List.empty[StorageUnit])
      usersRepo       <- Ref.make(UsersRepoFake.UsersRepoState())
      result          <- CollectionsService
                           .getCollectionDetailsAs(ExampleUserId, ExampleCollectionId)
                           .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                           .either
    } yield assert(result)(
      isLeft(equalTo(AuthorizationError(OperationNotPermitted(GetCollectionDetails(Subject(ExampleUserId), ExampleCollectionId)))))
    )
  }

  private val createCollection = testM("should create new collection") {
    val expectedCollection = Collection(FirstRandomFuuid, ExampleCollectionName, SecondRandomFuuid, ExampleUserId)

    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState()
    for {
      collectionsRepo          <- Ref.make(initCollectionsRepoState)
      filesRepo                <- Ref.make(List.empty[StorageUnit])
      usersRepo                <- Ref.make(UsersRepoFake.UsersRepoState())
      result                   <- CollectionsService
                                    .createCollectionAs(ExampleUserId, crateCollectionDto)
                                    .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
      finalCollectionRepoState <- collectionsRepo.get
    } yield assertTrue(result == expectedCollection) &&
      assert(finalCollectionRepoState.collections)(hasSameElements(Set(expectedCollection))) &&
      assert(finalCollectionRepoState.userCollections)(
        hasSameElements(Set(CollectionsRepoFake.UserCollection(FirstRandomFuuid, ExampleUserId, accepted = true)))
      ) &&
      assert(finalCollectionRepoState.permissions)(
        hasSameElements(
          Set(
            CollectionsRepoFake.PermissionEntry(FirstRandomFuuid, ExampleUserId, CollectionPermission.ManageCollection),
            CollectionsRepoFake.PermissionEntry(FirstRandomFuuid, ExampleUserId, CollectionPermission.CreateFile),
            CollectionsRepoFake.PermissionEntry(FirstRandomFuuid, ExampleUserId, CollectionPermission.ReadFile),
            CollectionsRepoFake.PermissionEntry(FirstRandomFuuid, ExampleUserId, CollectionPermission.ReadFileMetadata)
          )
        )
      )
  }

  private val readEncryptionKey = testM("should read encryption key of collection") {
    val expectedKey = EncryptionKey(ExampleCollectionId, ExampleUserId, ExampleEncryptionKeyValue, ExampleEncryptionKeyVersion)

    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(
        CollectionsRepoFake.UserCollection(ExampleCollectionId, ExampleUserId, accepted = true)
      ),
      collectionKeys = Set(expectedKey)
    )
    for {
      collectionsRepo          <- Ref.make(initCollectionsRepoState)
      filesRepo                <- Ref.make(List.empty[StorageUnit])
      usersRepo                <- Ref.make(UsersRepoFake.UsersRepoState())
      result                   <- CollectionsService
                                    .getEncryptionKeyAs(ExampleUserId, ExampleCollectionId)
                                    .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
      finalCollectionRepoState <- collectionsRepo.get
    } yield assertTrue(result.get == expectedKey) &&
      assertTrue(finalCollectionRepoState == initCollectionsRepoState)
  }

  private val readEncryptionKeyNotFound = testM("should not read encryption key of collection if key not exist") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(
        CollectionsRepoFake.UserCollection(ExampleCollectionId, ExampleUserId, accepted = true)
      )
    )
    for {
      collectionsRepo          <- Ref.make(initCollectionsRepoState)
      filesRepo                <- Ref.make(List.empty[StorageUnit])
      usersRepo                <- Ref.make(UsersRepoFake.UsersRepoState())
      result                   <- CollectionsService
                                    .getEncryptionKeyAs(ExampleUserId, ExampleCollectionId)
                                    .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
      finalCollectionRepoState <- collectionsRepo.get
    } yield assert(result)(isNone) &&
      assertTrue(finalCollectionRepoState == initCollectionsRepoState)
  }

  private val readEncryptionKeyNotPermitted = testM("should  not read encryption key of collection if user not allowed") {
    val key = EncryptionKey(ExampleCollectionId, ExampleUserId, ExampleEncryptionKeyValue, ExampleEncryptionKeyVersion)

    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(
        CollectionsRepoFake.UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)
      ),
      collectionKeys = Set(key)
    )
    for {
      collectionsRepo          <- Ref.make(initCollectionsRepoState)
      filesRepo                <- Ref.make(List.empty[StorageUnit])
      usersRepo                <- Ref.make(UsersRepoFake.UsersRepoState())
      result                   <- CollectionsService
                                    .getEncryptionKeyAs(ExampleUserId, ExampleCollectionId)
                                    .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                                    .either
      finalCollectionRepoState <- collectionsRepo.get
    } yield assert(result)(
      isLeft(equalTo(AuthorizationError(OperationNotPermitted(GetEncryptionKey(Subject(ExampleUserId), ExampleCollectionId)))))
    ) &&
      assertTrue(finalCollectionRepoState == initCollectionsRepoState)
  }

  private val readAllEncryptionKeysOfUser = testM("should read all encryption key of user") {
    val key1 = EncryptionKey(ExampleCollectionId, ExampleUserId, ExampleEncryptionKeyValue, ExampleEncryptionKeyVersion)
    val key2 = EncryptionKey(ExampleCollectionId, ExampleFuuid1, "foo", ExampleEncryptionKeyVersion)
    val key3 = EncryptionKey(ExampleFuuid2, ExampleUserId, "bar", ExampleEncryptionKeyVersion)
    val key4 = EncryptionKey(ExampleFuuid2, ExampleFuuid1, "baz", ExampleEncryptionKeyVersion)
    val key5 = EncryptionKey(ExampleFuuid3, ExampleFuuid1, "qux", ExampleEncryptionKeyVersion)

    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collectionKeys = Set(key1, key2, key3, key4, key5)
    )
    for {
      collectionsRepo          <- Ref.make(initCollectionsRepoState)
      filesRepo                <- Ref.make(List.empty[StorageUnit])
      usersRepo                <- Ref.make(UsersRepoFake.UsersRepoState())
      result                   <- CollectionsService
                                    .getEncryptionKeysForAllCollectionsOfUser(ExampleUserId)
                                    .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
      finalCollectionRepoState <- collectionsRepo.get
    } yield assert(result)(hasSameElements(Set(key1, key3))) &&
      assertTrue(finalCollectionRepoState == initCollectionsRepoState)
  }

  private val deleteCollection = testM("should delete collection") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection)
    )

    for {
      collectionsRepo          <- Ref.make(initCollectionsRepoState)
      filesRepo                <- Ref.make(List.empty[StorageUnit])
      usersRepo                <- Ref.make(UsersRepoFake.UsersRepoState())
      result                   <- CollectionsService
                                    .deleteCollectionAs(userId, collectionId)
                                    .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                                    .either
      finalCollectionRepoState <- collectionsRepo.get
    } yield assert(result)(isRight(isUnit)) &&
      assertTrue(finalCollectionRepoState.collections.isEmpty)
  }

  private val deleteCollectionNonExisting = testM("should not delete non existing collection") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection)
    )

    for {
      collectionsRepo          <- Ref.make(initCollectionsRepoState)
      filesRepo                <- Ref.make(List.empty[StorageUnit])
      usersRepo                <- Ref.make(UsersRepoFake.UsersRepoState())
      result                   <- CollectionsService
                                    .deleteCollectionAs(userId, CollectionId(ExampleFuuid1))
                                    .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                                    .either
      finalCollectionRepoState <- collectionsRepo.get
    } yield assert(result)(
      isLeft(equalTo(AuthorizationError(OperationNotPermitted(DeleteCollection(Subject(ExampleUserId), CollectionId(ExampleFuuid1))))))
    ) &&
      assertTrue(finalCollectionRepoState == initCollectionsRepoState)
  }

  private val deleteCollectionByNonOwner = testM("should not delete collection owned by another user") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection)
    )

    for {
      collectionsRepo          <- Ref.make(initCollectionsRepoState)
      filesRepo                <- Ref.make(List.empty[StorageUnit])
      usersRepo                <- Ref.make(UsersRepoFake.UsersRepoState())
      result                   <- CollectionsService
                                    .deleteCollectionAs(UserId(ExampleFuuid1), collectionId)
                                    .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                                    .either
      finalCollectionRepoState <- collectionsRepo.get
    } yield assert(result)(
      isLeft(equalTo(AuthorizationError(OperationNotPermitted(DeleteCollection(Subject(ExampleFuuid1), collectionId)))))
    ) &&
      assertTrue(finalCollectionRepoState == initCollectionsRepoState)
  }

  private val deleteNonEmptyCollection = testM("should not delete non empty collection") {
    val initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      collections = Set(ExampleCollection)
    )

    for {
      collectionsRepo          <- Ref.make(initCollectionsRepoState)
      filesRepo                <- Ref.make(List[StorageUnit](ExampleDirectoryMetadata))
      usersRepo                <- Ref.make(UsersRepoFake.UsersRepoState())
      result                   <- CollectionsService
                                    .deleteCollectionAs(userId, collectionId)
                                    .provideLayer(createService(collectionsRepo, filesRepo, usersRepo))
                                    .either
      finalCollectionRepoState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(CollectionNotEmpty(collectionId)))) &&
      assertTrue(finalCollectionRepoState == initCollectionsRepoState)
  }

}
