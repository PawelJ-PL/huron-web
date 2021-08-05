package com.github.huronapp.api.auth.authorization

import com.github.huronapp.api.auth.authorization.AuthorizationKernel.AuthorizationKernel
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.constants.{Collections, MiscConstants, Users}
import com.github.huronapp.api.domain.collections.CollectionPermission
import com.github.huronapp.api.testdoubles.CollectionsRepoFake
import com.github.huronapp.api.testdoubles.CollectionsRepoFake.{PermissionEntry, UserCollection}
import io.github.gaelrenoux.tranzactio.doobie.Database
import zio.{Ref, ZLayer}
import zio.test.Assertion.{equalTo, isLeft, isRight}
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

object AuthorizationKernelSpec extends DefaultRunnableSpec with Users with Collections with MiscConstants {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Authorization kernel suite")(
      getCollectionDetails,
      getCollectionDetailsIfCollectionNotAssignedToUser,
      setEncryptionKeyByKeyOwner,
      setEncryptionKeyByCollectionAdmin,
      setEncryptionKeyIfCollectionNotAssigned
    )

  private def authKernel(
    collectionsRepoState: Ref[CollectionsRepoFake.CollectionsRepoState]
  ): ZLayer[TestEnvironment, Nothing, AuthorizationKernel] =
    Database.none ++ CollectionsRepoFake.create(collectionsRepoState) >>> AuthorizationKernel.live

  val ExampleSubject: Subject = Subject(ExampleUserId)

  val GetCollectionDetailsOperation: GetCollectionDetails = GetCollectionDetails(ExampleSubject, ExampleCollectionId)

  val SetEncryptionKeyOperation: SetEncryptionKey = SetEncryptionKey(ExampleSubject, ExampleCollectionId, ExampleFuuid1)

  private val getCollectionDetails = testM("should allow to get collection details") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleUserId, accepted = true))
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(GetCollectionDetailsOperation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isRight(equalTo(())))
  }

  private val getCollectionDetailsIfCollectionNotAssignedToUser =
    testM("should not allow to get collection details if collection is not assigned to user") {
      val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
        userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true))
      )

      for {
        collectionsRepo <- Ref.make(collectionsRepoState)
        result          <- AuthorizationKernel.authorizeOperation(GetCollectionDetailsOperation).provideLayer(authKernel(collectionsRepo)).either
      } yield assert(result)(isLeft(equalTo(OperationNotPermitted(GetCollectionDetailsOperation))))
    }

  private val setEncryptionKeyByKeyOwner = testM("should allow to set encryption key to himself") {
    val operation = SetEncryptionKeyOperation.copy(userId = ExampleUserId)

    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleUserId, accepted = true))
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(operation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isRight(equalTo(())))
  }

  private val setEncryptionKeyByCollectionAdmin = testM("should allow to set encryption key by collection admin") {
    val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
      userCollections = Set(UserCollection(ExampleCollectionId, ExampleFuuid1, accepted = true)),
      permissions = Set(PermissionEntry(ExampleCollectionId, ExampleUserId, CollectionPermission.ManageCollection))
    )

    for {
      collectionsRepo <- Ref.make(collectionsRepoState)
      result          <- AuthorizationKernel.authorizeOperation(SetEncryptionKeyOperation).provideLayer(authKernel(collectionsRepo)).either
    } yield assert(result)(isRight(equalTo(())))
  }

  private val setEncryptionKeyIfCollectionNotAssigned =
    testM("should not allow to set encryption key is collection is not assigned to user") {
      val operation = SetEncryptionKeyOperation.copy(userId = ExampleUserId)

      val collectionsRepoState = CollectionsRepoFake.CollectionsRepoState()

      for {
        collectionsRepo <- Ref.make(collectionsRepoState)
        result          <- AuthorizationKernel.authorizeOperation(operation).provideLayer(authKernel(collectionsRepo)).either
      } yield assert(result)(isLeft(equalTo(OperationNotPermitted(operation))))
    }

}
