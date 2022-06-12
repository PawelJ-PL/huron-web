package com.github.huronapp.api.domain.collections

import cats.syntax.show._
import cats.data.Chain
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.auth.authorization.{
  GetCollectionMembers,
  InviteCollectionMember,
  ListMemberPermissions,
  OperationNotPermitted,
  RemoveCollectionMember,
  SetCollectionPermissions
}
import com.github.huronapp.api.constants.{Collections, MiscConstants, Users}
import com.github.huronapp.api.domain.collections.dto.NewMemberReq
import com.github.huronapp.api.domain.collections.dto.fields.EncryptedCollectionKey
import com.github.huronapp.api.domain.users.UserId
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import com.github.huronapp.api.http.ErrorResponse
import com.github.huronapp.api.testdoubles.HttpAuthenticationFake.validAuthHeader
import com.github.huronapp.api.testdoubles.{CollectionsServiceStub, HttpAuthenticationFake, LoggerFake}
import com.github.huronapp.api.utils.Implicits.fuuidCollectionPermissionsMap._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import io.chrisdavenport.fuuid.FUUID
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.implicits._
import org.http4s.{Method, Request, Status}
import zio.interop.catz._
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assertTrue}
import zio.{Has, Ref, ZIO, ZLayer}

object CollectionsRoutesMemberSpec extends DefaultRunnableSpec with Collections with Users with MiscConstants {

  private val userId = UserId(ExampleUserId)

  private val collectionId = CollectionId(ExampleCollectionId)

  private val inviteMemberDto = NewMemberReq(
    ExampleCollection.encryptionKeyVersion,
    EncryptedCollectionKey("abcd"),
    Refined.unsafeApply[List[CollectionPermission], NonEmpty](List(CollectionPermission.ReadFileMetadata))
  )

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Collection routes members suite")(
      listMembers,
      listMembersForbidden,
      listMembersUnauthorized,
      setPermissions,
      setPermissionsWithEmptyList,
      setPermissionsNotPermitted,
      setPermissionsForOwner,
      setPermissionsSelf,
      setPermissionsCollectionNotFound,
      setPermissionsNotAMember,
      setPermissionsUnauthorized,
      inviteMember,
      inviteMemberForbidden,
      inviteMemberCollectionNotFound,
      inviteMemberUserNotFound,
      inviteAlreadyJoinedMember,
      inviteMemberKeyVersionMismatch,
      inviteMemberUnauthorized,
      removeCollectionMember,
      removeCollectionMemberForbidden,
      removeCollectionOwner,
      removeMemberFromNonExistingCollection,
      removeNonMember,
      acceptInvitation,
      acceptInvitationNotFound,
      acceptInvitationAlreadyAccepted,
      acceptInvitationUnauthorized,
      cancelAcceptance,
      cancelAcceptInvitationNotFound,
      cancelAcceptanceInvitationNotAccepted,
      cancelAcceptanceUnauthorized,
      listMemberPermissions,
      listMemberPermissionsForbidden,
      listMemberPermissionsUnauthorized
    )

  def createRoutes(
    collectionServicesResponses: CollectionsServiceStub.CollectionsServiceResponses,
    logs: Ref[Chain[String]]
  ): ZLayer[TestEnvironment, Nothing, Has[CollectionsRoutes.Service]] =
    CollectionsServiceStub.withResponses(collectionServicesResponses) ++ LoggerFake.usingRef(
      logs
    ) ++ HttpAuthenticationFake.create >>> CollectionsRoutes.live

  private val listMembers = testM("should generate response for collection members list request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections".addSegment(ExampleCollectionId.show).addSegment("members")
      req = Request[RouteEffect](method = Method.GET, uri).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[Map[FUUID, List[CollectionPermission]]]
    } yield assertTrue(result.status == Status.Ok) &&
      assertTrue(body == Map(ExampleUserId -> List(CollectionPermission.ManageCollection, CollectionPermission.ReadFileMetadata)))
  }

  private val listMembersForbidden = testM("should generate response for collection members list request if user has no permissions") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses(
      getMembers =
        ZIO.fail(AuthorizationError(OperationNotPermitted(GetCollectionMembers(Subject(ExampleUserId), CollectionId(ExampleCollectionId)))))
    )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections".addSegment(ExampleCollectionId.show).addSegment("members")
      req = Request[RouteEffect](method = Method.GET, uri).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Forbidden]
    } yield assertTrue(result.status == Status.Forbidden) &&
      assertTrue(body == ErrorResponse.Forbidden("Operation not permitted"))
  }

  private val listMembersUnauthorized = testM("should generate response for collection members list request if user not logged in") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections".addSegment(ExampleCollectionId.show).addSegment("members")
      req = Request[RouteEffect](method = Method.GET, uri)
      result <- routes.run(req)
    } yield assertTrue(result.status == Status.Unauthorized)
  }

  private val setPermissions = testM("should generate response for set permissions request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs           <- Ref.make(Chain.empty[String])
      routes         <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
              .addSegment("permission")
      req = Request[RouteEffect](method = Method.PUT, uri)
              .withHeaders(validAuthHeader)
              .withEntity(List(CollectionPermission.ManageCollection.toString))
      result         <- routes.run(req)
      loggedMessages <- logs.get
    } yield assertTrue(result.status == Status.NoContent) &&
      assertTrue(loggedMessages.isEmpty)
  }

  private val setPermissionsWithEmptyList = testM("should generate response for set permissions request if permission list is empty") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs           <- Ref.make(Chain.empty[String])
      routes         <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
              .addSegment("permission")
      req = Request[RouteEffect](method = Method.PUT, uri)
              .withHeaders(validAuthHeader)
              .withEntity(List.empty[String])
      result         <- routes.run(req)
      loggedMessages <- logs.get
    } yield assertTrue(result.status == Status.BadRequest) &&
      assertTrue(loggedMessages.isEmpty)
  }

  private val setPermissionsNotPermitted =
    testM("should generate response for set permissions request if user has no permissions to this action") {
      val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses(setMemberPermissions =
        ZIO.fail(
          AuthorizationError(
            OperationNotPermitted(
              SetCollectionPermissions(Subject(ExampleUserId), CollectionId(ExampleCollectionId), UserId(ExampleUserId))
            )
          )
        )
      )

      for {
        logs           <- Ref.make(Chain.empty[String])
        routes         <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
        uri = uri"/api/v1/collections"
                .addSegment(ExampleCollectionId.show)
                .addSegment("members")
                .addSegment(ExampleUserId.show)
                .addSegment("permission")
        req = Request[RouteEffect](method = Method.PUT, uri)
                .withHeaders(validAuthHeader)
                .withEntity(List(CollectionPermission.ManageCollection.toString))
        result         <- routes.run(req)
        loggedMessages <- logs.get
      } yield assertTrue(result.status == Status.Forbidden) &&
        assertTrue(
          loggedMessages == Chain.one(
            show"Subject $ExampleUserId not authorized to assign permissions of collection $ExampleCollectionId to user $ExampleUserId"
          )
        )
    }

  private val setPermissionsForOwner = testM("should generate response for set permissions request if user changes owner permission") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses(setMemberPermissions =
      ZIO.fail(ChangeCollectionOwnerPermissionsNotAllowed(userId, userId, collectionId))
    )

    for {
      logs           <- Ref.make(Chain.empty[String])
      routes         <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
              .addSegment("permission")
      req = Request[RouteEffect](method = Method.PUT, uri)
              .withHeaders(validAuthHeader)
              .withEntity(List(CollectionPermission.ManageCollection.toString))
      result         <- routes.run(req)
      loggedMessages <- logs.get
    } yield assertTrue(result.status == Status.Forbidden) &&
      assertTrue(
        loggedMessages == Chain.one(
          show"User $ExampleUserId is trying to change permissions of user $ExampleUserId who is an owner of collection $ExampleCollectionId"
        )
      )
  }

  private val setPermissionsSelf = testM("should generate response for set permissions request if user changes self permission") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses(setMemberPermissions = ZIO.fail(ChangeSelfPermissionsNotAllowed(userId)))

    for {
      logs           <- Ref.make(Chain.empty[String])
      routes         <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
              .addSegment("permission")
      req = Request[RouteEffect](method = Method.PUT, uri)
              .withHeaders(validAuthHeader)
              .withEntity(List(CollectionPermission.ManageCollection.toString))
      result         <- routes.run(req)
      loggedMessages <- logs.get
    } yield assertTrue(result.status == Status.Forbidden) &&
      assertTrue(
        loggedMessages == Chain.one(
          show"User $ExampleUserId is going to change self permissions"
        )
      )
  }

  private val setPermissionsCollectionNotFound = testM("should generate response for set permissions request if collection not found") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses(setMemberPermissions = ZIO.fail(CollectionNotFound(ExampleCollectionId)))

    for {
      logs           <- Ref.make(Chain.empty[String])
      routes         <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
              .addSegment("permission")
      req = Request[RouteEffect](method = Method.PUT, uri)
              .withHeaders(validAuthHeader)
              .withEntity(List(CollectionPermission.ManageCollection.toString))
      result         <- routes.run(req)
      loggedMessages <- logs.get
    } yield assertTrue(result.status == Status.NotFound) &&
      assertTrue(loggedMessages == Chain.one(show"Collection $ExampleCollectionId not found"))
  }

  private val setPermissionsNotAMember = testM("should generate response for set permissions request if user is not a collection member") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses(setMemberPermissions = ZIO.fail(UserIsNotMemberOfCollection(userId, collectionId)))

    for {
      logs           <- Ref.make(Chain.empty[String])
      routes         <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
              .addSegment("permission")
      req = Request[RouteEffect](method = Method.PUT, uri)
              .withHeaders(validAuthHeader)
              .withEntity(List(CollectionPermission.ManageCollection.toString))
      result         <- routes.run(req)
      body           <- result.as[ErrorResponse.PreconditionFailed]
      loggedMessages <- logs.get
    } yield assertTrue(result.status == Status.PreconditionFailed) &&
      assertTrue(
        loggedMessages == Chain.one(
          show"User $userId is not member of collection $collectionId"
        )
      ) &&
      assertTrue(
        body == ErrorResponse.PreconditionFailed(
          show"User $ExampleUserId is not a member of collection $ExampleCollectionId",
          Some("UserIsNotAMember")
        )
      )
  }

  private val setPermissionsUnauthorized = testM("should generate response for set permissions request if user is not logged in") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs           <- Ref.make(Chain.empty[String])
      routes         <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
              .addSegment("permission")
      req = Request[RouteEffect](method = Method.PUT, uri).withEntity(List(CollectionPermission.ManageCollection.toString))
      result         <- routes.run(req)
      loggedMessages <- logs.get
    } yield assertTrue(result.status == Status.Unauthorized) &&
      assertTrue(loggedMessages.isEmpty)
  }

  private val inviteMember = testM("should generate response for invite member request") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
      req = Request[RouteEffect](method = Method.PUT, uri).withHeaders(validAuthHeader).withEntity(inviteMemberDto)
      result <- routes.run(req)
    } yield assertTrue(result.status == Status.NoContent)
  }

  private val inviteMemberForbidden = testM("should generate response for invite member request if user has no sufficient permissions") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses(
        inviteMember =
          ZIO.fail(AuthorizationError(OperationNotPermitted(InviteCollectionMember(Subject(ExampleUserId), collectionId, userId))))
      )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
      req = Request[RouteEffect](method = Method.PUT, uri).withHeaders(validAuthHeader).withEntity(inviteMemberDto)
      result <- routes.run(req)
    } yield assertTrue(result.status == Status.Forbidden)
  }

  private val inviteMemberCollectionNotFound = testM("should generate response for invite member request if collection not found") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses(
        inviteMember = ZIO.fail(CollectionNotFound(ExampleCollectionId))
      )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
      req = Request[RouteEffect](method = Method.PUT, uri).withHeaders(validAuthHeader).withEntity(inviteMemberDto)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assertTrue(result.status == Status.NotFound) &&
      assertTrue(body == ErrorResponse.NotFound("Collection not found"))
  }

  private val inviteMemberUserNotFound = testM("should generate response for invite member request if user not found") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses(
        inviteMember = ZIO.fail(UserNotFound(userId))
      )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
      req = Request[RouteEffect](method = Method.PUT, uri).withHeaders(validAuthHeader).withEntity(inviteMemberDto)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assertTrue(result.status == Status.NotFound) &&
      assertTrue(body == ErrorResponse.NotFound("User not found"))
  }

  private val inviteAlreadyJoinedMember = testM("should generate response for invite member request if user has already joined") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses(
        inviteMember = ZIO.fail(AlreadyMember(userId, collectionId))
      )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
      req = Request[RouteEffect](method = Method.PUT, uri).withHeaders(validAuthHeader).withEntity(inviteMemberDto)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assertTrue(result.status == Status.PreconditionFailed) &&
      assertTrue(body == ErrorResponse.PreconditionFailed("User is already member of collection", Some("AlreadyMember")))
  }

  private val inviteMemberKeyVersionMismatch = testM("should generate response for invite member request if key version mismatch") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses(
        inviteMember = ZIO.fail(KeyVersionMismatch(collectionId, ExampleCollection.encryptionKeyVersion, ExampleFuuid1))
      )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
      req = Request[RouteEffect](method = Method.PUT, uri).withHeaders(validAuthHeader).withEntity(inviteMemberDto)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.PreconditionFailed]
    } yield assertTrue(result.status == Status.PreconditionFailed) &&
      assertTrue(
        body == ErrorResponse.PreconditionFailed(
          show"Current encryption key for collection $collectionId is $ExampleFuuid1, but version ${ExampleCollection.encryptionKeyVersion} was requested",
          Some("KeyVersionMismatch")
        )
      )
  }

  private val inviteMemberUnauthorized = testM("should generate response for invite member request if user is not logged in") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
      req = Request[RouteEffect](method = Method.PUT, uri).withEntity(inviteMemberDto)
      result <- routes.run(req)
    } yield assertTrue(result.status == Status.Unauthorized)
  }

  private val removeCollectionMember = testM("should generate response for remove collection member request") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
      req = Request[RouteEffect](method = Method.DELETE, uri).withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assertTrue(result.status == Status.NoContent)
  }

  private val removeCollectionMemberForbidden =
    testM("should generate response for remove collection member request if user has no sufficient permissions") {
      val collectionServicesResponses =
        CollectionsServiceStub.CollectionsServiceResponses(
          deleteMember = ZIO.fail(
            AuthorizationError(OperationNotPermitted(RemoveCollectionMember(Subject(ExampleUserId), collectionId, UserId(ExampleFuuid1))))
          )
        )

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
        uri = uri"/api/v1/collections"
                .addSegment(ExampleCollectionId.show)
                .addSegment("members")
                .addSegment(ExampleFuuid1.show)
        req = Request[RouteEffect](method = Method.DELETE, uri).withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.Forbidden]
      } yield assertTrue(result.status == Status.Forbidden) &&
        assertTrue(body == ErrorResponse.Forbidden("Operation not permitted"))
    }

  private val removeCollectionOwner = testM("should generate response for remove collection member request if trying to remove owner") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses(deleteMember =
        ZIO.fail(RemoveCollectionOwnerNotAllowed(UserId(ExampleFuuid1), collectionId, userId))
      )

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
      req = Request[RouteEffect](method = Method.DELETE, uri).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.Forbidden]
    } yield assertTrue(result.status == Status.Forbidden) &&
      assertTrue(body == ErrorResponse.Forbidden("Collections owner can't be removed"))
  }

  private val removeMemberFromNonExistingCollection =
    testM("should generate response for remove collection member request if collection does not exist") {
      val collectionServicesResponses =
        CollectionsServiceStub.CollectionsServiceResponses(deleteMember = ZIO.fail(CollectionNotFound(ExampleCollectionId)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
        uri = uri"/api/v1/collections"
                .addSegment(ExampleCollectionId.show)
                .addSegment("members")
                .addSegment(ExampleUserId.show)
        req = Request[RouteEffect](method = Method.DELETE, uri).withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.NotFound]
      } yield assertTrue(result.status == Status.NotFound) &&
        assertTrue(body == ErrorResponse.NotFound("Collection not found"))
    }

  private val removeNonMember =
    testM("should generate response for remove collection member request if user is not collection member") {
      val collectionServicesResponses =
        CollectionsServiceStub.CollectionsServiceResponses(deleteMember = ZIO.fail(UserIsNotMemberOfCollection(userId, collectionId)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
        uri = uri"/api/v1/collections"
                .addSegment(ExampleCollectionId.show)
                .addSegment("members")
                .addSegment(ExampleUserId.show)
        req = Request[RouteEffect](method = Method.DELETE, uri).withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.PreconditionFailed]
      } yield assertTrue(result.status == Status.PreconditionFailed) &&
        assertTrue(
          body == ErrorResponse.PreconditionFailed(show"User $userId is not a member of collection $collectionId", Some("UserIsNotAMember"))
        )
    }

  private val acceptInvitation = testM("should generate response for accept invitation request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment("me")
              .addSegment("approval")
      req = Request[RouteEffect](method = Method.PUT, uri).withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assertTrue(result.status == Status.NoContent)
  }

  private val acceptInvitationNotFound = testM("should generate response for accept invitation request if invitation was not found") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses(acceptInvitation = ZIO.fail(InvitationNotFound(collectionId, userId)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment("me")
              .addSegment("approval")
      req = Request[RouteEffect](method = Method.PUT, uri).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assertTrue(result.status == Status.NotFound) &&
      assertTrue(body == ErrorResponse.NotFound("Invitation not found"))
  }

  private val acceptInvitationAlreadyAccepted =
    testM("should generate response for accept invitation request if invitation was already accepted") {
      val collectionServicesResponses =
        CollectionsServiceStub.CollectionsServiceResponses(acceptInvitation = ZIO.fail(InvitationAlreadyAccepted(collectionId, userId)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
        uri = uri"/api/v1/collections"
                .addSegment(ExampleCollectionId.show)
                .addSegment("members")
                .addSegment("me")
                .addSegment("approval")
        req = Request[RouteEffect](method = Method.PUT, uri).withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.PreconditionFailed]
      } yield assertTrue(result.status == Status.PreconditionFailed) &&
        assertTrue(body == ErrorResponse.PreconditionFailed("Invitation already accepted", Some("InvitationAlreadyAccepted")))
    }

  private val acceptInvitationUnauthorized =
    testM("should generate response for accept invitation request if user not logged in") {
      val collectionServicesResponses =
        CollectionsServiceStub.CollectionsServiceResponses(acceptInvitation = ZIO.fail(InvitationAlreadyAccepted(collectionId, userId)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
        uri = uri"/api/v1/collections"
                .addSegment(ExampleCollectionId.show)
                .addSegment("members")
                .addSegment("me")
                .addSegment("approval")
        req = Request[RouteEffect](method = Method.PUT, uri)
        result <- routes.run(req)
      } yield assertTrue(result.status == Status.Unauthorized)
    }

  private val cancelAcceptance = testM("should generate response for cancel invitation acceptance request") {
    val collectionServicesResponses = CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment("me")
              .addSegment("approval")
      req = Request[RouteEffect](method = Method.DELETE, uri).withHeaders(validAuthHeader)
      result <- routes.run(req)
    } yield assertTrue(result.status == Status.NoContent)
  }

  private val cancelAcceptInvitationNotFound = testM("should generate response for accept invitation request if invitation was not found") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses(cancelAcceptance = ZIO.fail(InvitationNotFound(collectionId, userId)))

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment("me")
              .addSegment("approval")
      req = Request[RouteEffect](method = Method.DELETE, uri).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[ErrorResponse.NotFound]
    } yield assertTrue(result.status == Status.NotFound) &&
      assertTrue(body == ErrorResponse.NotFound("Invitation not found"))
  }

  private val cancelAcceptanceInvitationNotAccepted =
    testM("should generate response for accept invitation request if invitation was not accepted") {
      val collectionServicesResponses =
        CollectionsServiceStub.CollectionsServiceResponses(cancelAcceptance = ZIO.fail(InvitationNotAccepted(collectionId, userId)))

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
        uri = uri"/api/v1/collections"
                .addSegment(ExampleCollectionId.show)
                .addSegment("members")
                .addSegment("me")
                .addSegment("approval")
        req = Request[RouteEffect](method = Method.DELETE, uri).withHeaders(validAuthHeader)
        result <- routes.run(req)
        body   <- result.as[ErrorResponse.PreconditionFailed]
      } yield assertTrue(result.status == Status.PreconditionFailed) &&
        assertTrue(body == ErrorResponse.PreconditionFailed("Invitation is not accepted", Some("InvitationNotAccepted")))
    }

  private val cancelAcceptanceUnauthorized =
    testM("should generate response for cancel invitation acceptance request if user not logged in") {
      val collectionServicesResponses =
        CollectionsServiceStub.CollectionsServiceResponses()

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
        uri = uri"/api/v1/collections"
                .addSegment(ExampleCollectionId.show)
                .addSegment("members")
                .addSegment("me")
                .addSegment("approval")
        req = Request[RouteEffect](method = Method.DELETE, uri)
        result <- routes.run(req)
      } yield assertTrue(result.status == Status.Unauthorized)
    }

  private val listMemberPermissions = testM("should generate response for list permissions request") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
              .addSegment("permission")
      req = Request[RouteEffect](method = Method.GET, uri).withHeaders(validAuthHeader)
      result <- routes.run(req)
      body   <- result.as[List[CollectionPermission]]
    } yield assertTrue(result.status == Status.Ok) &&
      assertTrue(body == List(CollectionPermission.ReadFileMetadata, CollectionPermission.ReadFile))
  }

  private val listMemberPermissionsForbidden =
    testM("should generate response for list permissions request if user has no sufficient privileges") {
      val collectionServicesResponses =
        CollectionsServiceStub.CollectionsServiceResponses(listPermissions =
          ZIO.fail(AuthorizationError(OperationNotPermitted(ListMemberPermissions(Subject(ExampleUserId), collectionId, userId))))
        )

      for {
        logs   <- Ref.make(Chain.empty[String])
        routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
        uri = uri"/api/v1/collections"
                .addSegment(ExampleCollectionId.show)
                .addSegment("members")
                .addSegment(ExampleUserId.show)
                .addSegment("permission")
        req = Request[RouteEffect](method = Method.GET, uri).withHeaders(validAuthHeader)
        result <- routes.run(req)
      } yield assertTrue(result.status == Status.Forbidden)
    }

  private val listMemberPermissionsUnauthorized = testM("should generate response for list permissions request if user not logged in") {
    val collectionServicesResponses =
      CollectionsServiceStub.CollectionsServiceResponses()

    for {
      logs   <- Ref.make(Chain.empty[String])
      routes <- CollectionsRoutes.routes.provideLayer(createRoutes(collectionServicesResponses, logs)).map(_.orNotFound)
      uri = uri"/api/v1/collections"
              .addSegment(ExampleCollectionId.show)
              .addSegment("members")
              .addSegment(ExampleUserId.show)
              .addSegment("permission")
      req = Request[RouteEffect](method = Method.GET, uri)
      result <- routes.run(req)
    } yield assertTrue(result.status == Status.Unauthorized)
  }

}
