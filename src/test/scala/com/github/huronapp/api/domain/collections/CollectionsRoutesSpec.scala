package com.github.huronapp.api.domain.collections

import cats.data.Chain
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.auth.authorization.{DeleteCollection, GetCollectionDetails, GetEncryptionKey, OperationNotPermitted}
import com.github.huronapp.api.constants.{Collections, Users}
import com.github.huronapp.api.domain.collections.dto.fields.{CollectionName, EncryptedCollectionKey}
import com.github.huronapp.api.domain.collections.dto.{UserCollectionData, EncryptionKeyData, NewCollectionReq}
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import com.github.huronapp.api.http.ErrorResponse
import com.github.huronapp.api.testdoubles.HttpAuthenticationFake.validAuthHeader
import com.github.huronapp.api.testdoubles.{CollectionsServiceStub, HttpAuthenticationFake, LoggerFake}
import io.circe.Json
import org.http4s.{Method, Request, Status, Uri}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.implicits._
import zio.{Has, Ref, ZIO, ZLayer}
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assertTrue}
import zio.interop.catz._

object CollectionsRoutesSpec extends DefaultRunnableSpec with Collections with Users {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Collection routes suite")(
      malformedBodyUnauthorized,
      listCollections,
      listCollectionsUnauthorized,
      getCollection,
      getCollectionNotPermitted,
      getCollectionNotFound,
      getCollectionUnauthorized,
      createCollection,
      createCollectionUnauthorized,
      getEncryptionKey,
      getEncryptionKeyNotFound,
      getEncryptionKeyForbidden,
      getEncryptionKeyNotAuthorized,
      getAllEncryptionKeys,
      getAllEncryptionKeysUnauthorized,
      deleteCollection,
      deleteCollectionForbidden,
      deleteNonEmptyCollection,
      deleteCollectionUnauthorized
    )

  def createRoutes(
    collectionServicesResponses: CollectionsServiceStub.CollectionsServiceResponses,
    logs: Ref[Chain[String]]
  ): ZLayer[TestEnvironment, Nothing, Has[CollectionsRoutes.Service]] =
    CollectionsServiceStub.withResponses(collectionServicesResponses) ++ LoggerFake.usingRef(
      logs
    ) ++ HttpAuthenticationFake.create >>> CollectionsRoutes.live

  private val encryptedKey = "1bae4c89ae32ef"

  private val createCollectionDto = NewCollectionReq(CollectionName(ExampleCollectionName), EncryptedCollectionKey(encryptedKey))

  private val collectionId = CollectionId(ExampleCollectionId)

  private val authSubject = Subject(ExampleUserId)

  private val malformedBodyUnauthorized = testM("should return unauthorized without information about malformed body") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri"/api/v1/collections").withEntity(List("foo"))
      result <- routes.run(req)
      body   <- result.as[Json]
    } yield assertTrue(result.status == Status.Unauthorized) &&
      assertTrue(body == Json.obj(("message", Json.fromString("Invalid credentials"))))
  }

  private val listCollections = testM("should generate response for collection list request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri"/api/v1/collections").withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[List[UserCollectionData]]
    } yield assertTrue(result.status == Status.Ok) &&
      assertTrue(
        body == List(
          UserCollectionData(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion, ExampleUserId, isAccepted = true)
        )
      )
  }

  private val listCollectionsUnauthorized = testM("should generate response for collection list request if user is unauthorized") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri"/api/v1/collections")
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Unauthorized]
    } yield assertTrue(result.status == Status.Unauthorized) &&
      assertTrue(body == ErrorResponse.Unauthorized("Invalid credentials"))
  }

  private val getCollection = testM("should generate response for get collection request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, Uri.unsafeFromString(s"/api/v1/collections/${ExampleCollectionId.toString()}"))
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[UserCollectionData]
    } yield assertTrue(result.status == Status.Ok) &&
      assertTrue(
        body == UserCollectionData(
          ExampleCollectionId,
          ExampleCollectionName,
          ExampleEncryptionKeyVersion,
          ExampleUserId,
          isAccepted = true
        )
      )
  }

  private val getCollectionNotPermitted = testM("should generate response for get collection request if action is not permitted") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses(
      getCollectionDetails =
        ZIO.fail(AuthorizationError(OperationNotPermitted(GetCollectionDetails(Subject(ExampleUserId), ExampleCollectionId))))
    )

    for {
      logs      <- Ref.make(Chain.empty[String])
      routes    <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, Uri.unsafeFromString(s"/api/v1/collections/${ExampleCollectionId.toString()}"))
              .withHeaders(validAuthHeader)
      result    <- routes.run(req)
      body      <- result.as[ErrorResponse.Forbidden]
      finalLogs <- logs.get
    } yield assertTrue(result.status == Status.Forbidden) &&
      assertTrue(body == ErrorResponse.Forbidden("Operation not permitted")) &&
      assertTrue(finalLogs == Chain.one(s"Subject $ExampleUserId not authorized to read details of collection $ExampleCollectionId"))
  }

  private val getCollectionNotFound = testM("should generate response for get collection request if collection not found") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses(
      getCollectionDetails = ZIO.fail(CollectionNotFound(ExampleCollectionId))
    )

    for {
      logs      <- Ref.make(Chain.empty[String])
      routes    <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, Uri.unsafeFromString(s"/api/v1/collections/${ExampleCollectionId.toString()}"))
              .withHeaders(validAuthHeader)
      result    <- routes.run(req)
      body      <- result.as[ErrorResponse.Forbidden]
      finalLogs <- logs.get
    } yield assertTrue(result.status == Status.Forbidden) &&
      assertTrue(body == ErrorResponse.Forbidden("Operation not permitted")) &&
      assertTrue(finalLogs == Chain.one(s"Collection $ExampleCollectionId not found"))
  }

  private val getCollectionUnauthorized = testM("should generate response for get collection request if user is not authorized") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, Uri.unsafeFromString(s"/api/v1/collections/${ExampleCollectionId.toString()}"))
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Unauthorized]
    } yield assertTrue(result.status == Status.Unauthorized) &&
      assertTrue(body == ErrorResponse.Unauthorized("Invalid credentials"))
  }

  private val createCollection = testM("should generate response for create collection request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req =
        Request[RouteEffect](method = Method.POST, uri"/api/v1/collections").withHeaders(validAuthHeader).withEntity(createCollectionDto)
      result <- routes.run(req)
      body   <- result.as[UserCollectionData]
    } yield assertTrue(result.status == Status.Ok) &&
      assertTrue(
        body == UserCollectionData(
          ExampleCollectionId,
          ExampleCollectionName,
          ExampleEncryptionKeyVersion,
          ExampleUserId,
          isAccepted = true
        )
      )
  }

  private val createCollectionUnauthorized = testM("should generate response for create collection request if user is unauthorized") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri"/api/v1/collections").withEntity(createCollectionDto)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Unauthorized]
    } yield assertTrue(result.status == Status.Unauthorized) &&
      assertTrue(body == ErrorResponse.Unauthorized("Invalid credentials"))
  }

  private val getEncryptionKey = testM("should generate response for get encryption key request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](
              method = Method.GET,
              Uri.unsafeFromString(s"/api/v1/collections/${ExampleCollectionId.toString()}/encryption-key")
            )
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[EncryptionKeyData]
    } yield assertTrue(result.status == Status.Ok) &&
      assertTrue(body == EncryptionKeyData(ExampleCollectionId, ExampleEncryptionKeyValue, ExampleEncryptionKeyVersion))
  }

  private val getEncryptionKeyNotFound = testM("should generate response for get encryption key request if key not set") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses(getEncryptionKey = ZIO.none)

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](
              method = Method.GET,
              Uri.unsafeFromString(s"/api/v1/collections/${ExampleCollectionId.toString()}/encryption-key")
            )
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assertTrue(result.status == Status.NotFound) &&
      assertTrue(body == ErrorResponse.NotFound(s"Encryption key not found for collection $ExampleCollectionId"))
  }

  private val getEncryptionKeyForbidden = testM("should generate response for get encryption key request if user not allowed") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses(getEncryptionKey =
      ZIO.fail(AuthorizationError(OperationNotPermitted(GetEncryptionKey(Subject(ExampleUserId), ExampleCollectionId))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](
              method = Method.GET,
              Uri.unsafeFromString(s"/api/v1/collections/${ExampleCollectionId.toString()}/encryption-key")
            )
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Forbidden]
    } yield assertTrue(result.status == Status.Forbidden) &&
      assertTrue(body == ErrorResponse.Forbidden("Operation not permitted"))
  }

  private val getEncryptionKeyNotAuthorized = testM("should generate response for get encryption key request if user not logged in") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](
              method = Method.GET,
              Uri.unsafeFromString(s"/api/v1/collections/${ExampleCollectionId.toString()}/encryption-key")
            )
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Unauthorized]
    } yield assertTrue(result.status == Status.Unauthorized) &&
      assertTrue(body == ErrorResponse.Unauthorized("Invalid credentials"))
  }

  private val getAllEncryptionKeys = testM("should generate response for get all encryption keys request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri"/api/v1/collections/encryption-key").withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[List[EncryptionKeyData]]
    } yield assertTrue(result.status == Status.Ok) &&
      assertTrue(body == List(EncryptionKeyData(ExampleCollectionId, ExampleEncryptionKeyValue, ExampleEncryptionKeyVersion)))
  }

  private val getAllEncryptionKeysUnauthorized =
    testM("should generate response for get all encryption keys request if user not logged in") {
      val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
        req = Request[RouteEffect](method = Method.GET, uri"/api/v1/collections/encryption-key")
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.Unauthorized]
      } yield assertTrue(result.status == Status.Unauthorized) &&
        assertTrue(body == ErrorResponse.Unauthorized("Invalid credentials"))
    }

  private val deleteCollection = testM("should generate response for collection delete request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections".addSegment(ExampleCollectionId.show)
      req = Request[RouteEffect](method = Method.DELETE, uri).withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assertTrue(result.status == Status.NoContent)
  }

  private val deleteCollectionForbidden =
    testM("should generate response for collection delete request if user has no sufficient permissions") {
      val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses(
        deleteCollection = ZIO.fail(AuthorizationError(OperationNotPermitted(DeleteCollection(authSubject, collectionId))))
      )

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
        uri = uri"/api/v1/collections".addSegment(ExampleCollectionId.show)
        req = Request[RouteEffect](method = Method.DELETE, uri).withHeaders(validAuthHeader)
        result <- routes.run(req)
      } yield assertTrue(result.status == Status.Forbidden)
    }

  private val deleteNonEmptyCollection = testM("should generate response for collection delete request if collection is not empty") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses(
      deleteCollection = ZIO.fail(CollectionNotEmpty(collectionId))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections".addSegment(ExampleCollectionId.show)
      req = Request[RouteEffect](method = Method.DELETE, uri).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assertTrue(result.status == Status.PreconditionFailed) &&
      assertTrue(body == ErrorResponse.PreconditionFailed("Collection not empty", Some("CollectionNotEmpty")))
  }

  private val deleteCollectionUnauthorized = testM("should generate response for collection delete request if user is no logged in") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections".addSegment(ExampleCollectionId.show)
      req = Request[RouteEffect](method = Method.DELETE, uri)
      result <- routes.run(req)
    } yield assertTrue(result.status == Status.Unauthorized)
  }

}
