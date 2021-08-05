package com.github.huronapp.api.domain.collections

import cats.data.Chain
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.auth.authorization.{GetCollectionDetails, OperationNotPermitted}
import com.github.huronapp.api.constants.{Collections, Users}
import com.github.huronapp.api.domain.collections.dto.fields.{CollectionName, EncryptedCollectionKey}
import com.github.huronapp.api.domain.collections.dto.{CollectionData, NewCollectionReq}
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import com.github.huronapp.api.http.ErrorResponse
import com.github.huronapp.api.testdoubles.HttpAuthenticationFake.validAuthHeader
import com.github.huronapp.api.testdoubles.{CollectionsServiceStub, HttpAuthenticationFake, LoggerFake}
import org.http4s.{Method, Request, Status, Uri}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.implicits._
import zio.{Has, Ref, ZIO, ZLayer}
import zio.test.Assertion.equalTo
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert}
import zio.interop.catz._

object CollectionsRoutesSpec extends DefaultRunnableSpec with Collections with Users {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Collection routes suite")(
      listCollections,
      listCollectionsUnauthorized,
      getCollection,
      getCollectionNotPermitted,
      getCollectionNotFound,
      getCollectionUnauthorized,
      createCollection,
      createCollectionUnauthorized
    )

  def createRoutes(
    collectionServicesResponses: CollectionsServiceStub.CollectionsServiceResponses,
    logs: Ref[Chain[String]]
  ): ZLayer[TestEnvironment, Nothing, Has[CollectionsRoutes.Service]] =
    CollectionsServiceStub.withResponses(collectionServicesResponses) ++ LoggerFake.usingRef(
      logs
    ) ++ HttpAuthenticationFake.create >>> CollectionsRoutes.live

  private val encryptedKey = "AES-CBC:1af4:16:32:1bae4c:89ae32ef"

  private val createCollectionDto = NewCollectionReq(CollectionName(ExampleCollectionName), EncryptedCollectionKey(encryptedKey))

  private val listCollections = testM("should generate response for collection list request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri"/api/v1/collections").withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[List[CollectionData]]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(equalTo(List(CollectionData(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion))))
  }

  private val listCollectionsUnauthorized = testM("should generate response for collection list request if user is unauthorized") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, uri"/api/v1/collections")
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Unauthorized]
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(body)(equalTo(ErrorResponse.Unauthorized("Invalid credentials")))
  }

  private val getCollection = testM("should generate response for get collection request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, Uri.unsafeFromString(s"/api/v1/collections/${ExampleCollectionId.toString()}"))
              .withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[CollectionData]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(equalTo(CollectionData(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion)))
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
    } yield assert(result.status)(equalTo(Status.Forbidden)) &&
      assert(body)(equalTo(ErrorResponse.Forbidden("Operation not permitted"))) &&
      assert(finalLogs)(equalTo(Chain.one(s"Subject $ExampleUserId not authorized to read details of collection $ExampleCollectionId")))
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
    } yield assert(result.status)(equalTo(Status.Forbidden)) &&
      assert(body)(equalTo(ErrorResponse.Forbidden("Operation not permitted"))) &&
      assert(finalLogs)(equalTo(Chain.one(s"Collection $ExampleCollectionId not found")))
  }

  private val getCollectionUnauthorized = testM("should generate response for get collection request if user is not authorized") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.GET, Uri.unsafeFromString(s"/api/v1/collections/${ExampleCollectionId.toString()}"))
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Unauthorized]
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(body)(equalTo(ErrorResponse.Unauthorized("Invalid credentials")))
  }

  private val createCollection = testM("should generate response for create collection request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req =
        Request[RouteEffect](method = Method.POST, uri"/api/v1/collections").withHeaders(validAuthHeader).withEntity(createCollectionDto)
      result <- routes.run(req)
      body   <- result.as[CollectionData]
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(equalTo(CollectionData(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion)))
  }

  private val createCollectionUnauthorized = testM("should generate response for create collection request if user is unauthorized") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      req = Request[RouteEffect](method = Method.POST, uri"/api/v1/collections").withEntity(createCollectionDto)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Unauthorized]
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(body)(equalTo(ErrorResponse.Unauthorized("Invalid credentials")))
  }

}
