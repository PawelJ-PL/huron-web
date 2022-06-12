package com.github.huronapp.api.domain.collections

import cats.data.NonEmptyList
import cats.syntax.show._
import com.github.huronapp.api.auth.authentication.AuthenticationInputs
import com.github.huronapp.api.auth.authentication.TapirAuthenticationInputs.authRequestParts
import com.github.huronapp.api.domain.collections.dto.fields.EncryptedCollectionKey
import com.github.huronapp.api.domain.collections.dto.{UserCollectionData, EncryptionKeyData, NewCollectionReq, NewMemberReq}
import com.github.huronapp.api.domain.users.UserId
import com.github.huronapp.api.http.{BaseEndpoint, ErrorResponse}
import com.github.huronapp.api.utils.Implicits.fuuid._
import com.github.huronapp.api.utils.Implicits.collectionId._
import com.github.huronapp.api.utils.Implicits.userId._
import com.github.huronapp.api.utils.Implicits.nonEmptyList._
import com.github.huronapp.api.utils.Implicits.fuuidCollectionPermissionsMap._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import io.chrisdavenport.fuuid.FUUID
import sttp.model.StatusCode
import sttp.tapir.codec.enumeratum._
import sttp.tapir.codec.refined._
import sttp.tapir.EndpointIO.Example
import sttp.tapir.{Endpoint, PublicEndpoint}
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._

object CollectionsEndpoints extends BaseEndpoint {

  object Responses {

    def userNotAMember(userId: UserId, collectionId: CollectionId): ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed(show"User $userId is not a member of collection $collectionId", Some("UserIsNotAMember"))

    val collectionNotEmpty: ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed("Collection not empty", Some("CollectionNotEmpty"))

    val alreadyMember: ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed("User is already member of collection", Some("AlreadyMember"))

    def keyVersionMismatch(collectionId: CollectionId, requestedVersion: FUUID, realVersion: FUUID): ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed(
        show"Current encryption key for collection $collectionId is $realVersion, but version $requestedVersion was requested",
        Some("KeyVersionMismatch")
      )

    val invitationAlreadyAccepted: ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed("Invitation already accepted", Some("InvitationAlreadyAccepted"))

    val invitationNotAccepted: ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed("Invitation is not accepted", Some("InvitationNotAccepted"))

  }

  private val exampleCollectionId = CollectionId(FUUID.fuuid("de3ad125-f16f-49ef-81d4-7a0d17b2a73b"))

  private val exampleUserId = UserId(FUUID.fuuid("6b789c88-cbe0-4e39-9922-77f1fd1dba9a"))

  private val notAMemberExample = Example(
    Responses.userNotAMember(exampleUserId, exampleCollectionId),
    Responses.userNotAMember(exampleUserId, exampleCollectionId).reason,
    Responses.userNotAMember(exampleUserId, exampleCollectionId).reason
  )

  private val collectionNotEmptyExample =
    Example(Responses.collectionNotEmpty, Responses.collectionNotEmpty.reason, Responses.collectionNotEmpty.reason)

  private val alreadyMemberExample = Example(Responses.alreadyMember, Responses.alreadyMember.reason, Responses.alreadyMember.reason)

  private val keyVersionMismatchExample = {
    val exampleResponse = Responses.keyVersionMismatch(
      exampleCollectionId,
      FUUID.fuuid("adfc0dd3-49fb-4ad7-9a1e-e9bbe46fbbf1"),
      FUUID.fuuid("d5aaae4a-bf55-41cb-9247-b9398019a29e")
    )
    Example(exampleResponse, exampleResponse.reason, exampleResponse.reason)
  }

  private val invitationAlreadyAcceptedExample =
    Example(Responses.invitationAlreadyAccepted, Responses.invitationAlreadyAccepted.reason, Responses.invitationAlreadyAccepted.reason)

  private val invitationNotAcceptedExample =
    Example(Responses.invitationNotAccepted, Responses.invitationNotAccepted.reason, Responses.invitationNotAccepted.reason)

  private val collectionsEndpoint: PublicEndpoint[Unit, Unit, Unit, Any] = publicApiEndpoint.tag("collections").in("collections")

  val listCollectionsEndpoint: Endpoint[AuthenticationInputs, Option[Boolean], ErrorResponse, List[UserCollectionData], Any] =
    collectionsEndpoint
      .summary("List collections available to user")
      .get
      .securityIn(authRequestParts)
      .in(query[Option[Boolean]]("onlyAccepted").description("Should return only collections accepted by user (default false)"))
      .out(jsonBody[List[UserCollectionData]])
      .errorOut(oneOf[ErrorResponse](unauthorized))

  val getCollectionDetailsEndpoint: Endpoint[AuthenticationInputs, FUUID, ErrorResponse, UserCollectionData, Any] =
    collectionsEndpoint
      .summary("Get collection details")
      .get
      .securityIn(authRequestParts)
      .in(path[FUUID]("collectionId"))
      .out(jsonBody[UserCollectionData])
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden
        )
      )

  val createCollectionEndpoint: Endpoint[AuthenticationInputs, NewCollectionReq, ErrorResponse, UserCollectionData, Any] =
    collectionsEndpoint
      .summary("Create new collection")
      .post
      .securityIn(authRequestParts)
      .in(jsonBody[NewCollectionReq])
      .out(jsonBody[UserCollectionData])
      .errorOut(oneOf[ErrorResponse](badRequest, unauthorized))

  val deleteCollectionEndpoint: Endpoint[AuthenticationInputs, CollectionId, ErrorResponse, Unit, Any] = collectionsEndpoint
    .summary("Delete collection")
    .delete
    .securityIn(authRequestParts)
    .in(path[CollectionId]("collectionId"))
    .out(statusCode(StatusCode.NoContent))
    .errorOut(
      oneOf[ErrorResponse](
        badRequest,
        unauthorized,
        forbidden,
        oneOfVariant(
          StatusCode.PreconditionFailed,
          jsonBody[ErrorResponse.PreconditionFailed]
            .examples(List(collectionNotEmptyExample))
        )
      )
    )

  val getSingleCollectionKeyEndpoint: Endpoint[AuthenticationInputs, FUUID, ErrorResponse, EncryptionKeyData, Any] =
    collectionsEndpoint
      .summary("Get encryption key for collection")
      .get
      .securityIn(authRequestParts)
      .in(path[FUUID]("collectionId") / "encryption-key")
      .out(jsonBody[EncryptionKeyData])
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Key not set for collection"))
        )
      )

  val getEncryptionKeysForAllCollectionsEndpoint: Endpoint[AuthenticationInputs, Unit, ErrorResponse, List[EncryptionKeyData], Any] =
    collectionsEndpoint
      .summary("Get encryption keys for all collections")
      .get
      .securityIn(authRequestParts)
      .in("encryption-key")
      .out(jsonBody[List[EncryptionKeyData]])
      .errorOut(oneOf[ErrorResponse](unauthorized))

  val inviteCollectionMemberEndpoint: Endpoint[AuthenticationInputs, (CollectionId, UserId, NewMemberReq), ErrorResponse, Unit, Any] =
    collectionsEndpoint
      .summary("Invite member")
      .put
      .securityIn(authRequestParts)
      .in(path[CollectionId]("collectionId") / "members" / path[UserId]("memberId"))
      .in(
        jsonBody[NewMemberReq].example(
          NewMemberReq(
            FUUID.fuuid("55e1344c-9ed7-40f1-9e7f-aa3930a6c5f5"),
            EncryptedCollectionKey("b2011af1a0a5C3972715cAD43CEEAFEF7C3eEFa700Aed3DD041853aDa8703bb2FfDa"),
            Refined.unsafeApply[List[CollectionPermission], NonEmpty](List(CollectionPermission.ReadFileMetadata))
          )
        )
      )
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Collection or user not found")),
          oneOfVariant(
            StatusCode.PreconditionFailed,
            jsonBody[ErrorResponse].examples(List(alreadyMemberExample, keyVersionMismatchExample))
          )
        )
      )

  val acceptInvitationEndpoint: Endpoint[AuthenticationInputs, CollectionId, ErrorResponse, Unit, Any] = collectionsEndpoint
    .summary("Accept invitation to collection")
    .put
    .securityIn(authRequestParts)
    .in(path[CollectionId]("collectionId") / "members" / "me" / "approval")
    .out(statusCode(StatusCode.NoContent))
    .errorOut(
      oneOf[ErrorResponse](
        badRequest,
        unauthorized,
        oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Invitation not found")),
        oneOfVariant(
          StatusCode.PreconditionFailed,
          jsonBody[ErrorResponse].examples(List(invitationAlreadyAcceptedExample))
        )
      )
    )

  val cancelInvitationAcceptanceEndpoint: Endpoint[AuthenticationInputs, CollectionId, ErrorResponse, Unit, Any] = collectionsEndpoint
    .summary("Cancel invitation to collection acceptance")
    .delete
    .securityIn(authRequestParts)
    .in(path[CollectionId]("collectionId") / "members" / "me" / "approval")
    .out(statusCode(StatusCode.NoContent))
    .errorOut(
      oneOf[ErrorResponse](
        badRequest,
        unauthorized,
        oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Invitation not found")),
        oneOfVariant(
          StatusCode.PreconditionFailed,
          jsonBody[ErrorResponse].examples(List(invitationNotAcceptedExample))
        )
      )
    )

  val getCollectionMembersEndpoint
    : Endpoint[AuthenticationInputs, CollectionId, ErrorResponse, Map[FUUID, List[CollectionPermission]], Any] = collectionsEndpoint
    .summary("Get collection members")
    .get
    .securityIn(authRequestParts)
    .in(path[CollectionId]("collectionId") / "members")
    .out(
      jsonBody[Map[FUUID, List[CollectionPermission]]].example(
        Map(
          FUUID.fuuid("191c4a54-b8d3-48a9-bff9-6ce30c0f9a18") -> List(CollectionPermission.ReadFile, CollectionPermission.ReadFileMetadata)
        )
      )
    )
    .errorOut(oneOf[ErrorResponse](unauthorized, forbidden))

  val listPermissionsEndpoint: Endpoint[AuthenticationInputs, (CollectionId, UserId), ErrorResponse, List[CollectionPermission], Any] =
    collectionsEndpoint
      .summary("List member permissions")
      .get
      .securityIn(authRequestParts)
      .in(path[CollectionId]("collectionId") / "members" / path[UserId]("memberId") / "permission")
      .out(jsonBody[List[CollectionPermission]])
      .errorOut(
        oneOf[ErrorResponse](
          unauthorized,
          forbidden
        )
      )

  val setPermissionsEndpoint
    : Endpoint[AuthenticationInputs, (CollectionId, UserId, Refined[List[CollectionPermission], NonEmpty]), ErrorResponse, Unit, Any] =
    collectionsEndpoint
      .summary("Set member permissions")
      .put
      .securityIn(authRequestParts)
      .in(path[CollectionId]("collectionId") / "members" / path[UserId]("memberId") / "permission")
      .in(jsonBody[List[CollectionPermission] Refined NonEmpty])
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[ErrorResponse](
          unauthorized,
          forbidden,
          oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound]),
          oneOfVariant(
            StatusCode.PreconditionFailed,
            jsonBody[ErrorResponse.PreconditionFailed]
              .examples(List(notAMemberExample))
          )
        )
      )

  val removeCollectionMemberEndpoint: Endpoint[AuthenticationInputs, (CollectionId, UserId), ErrorResponse, Unit, Any] =
    collectionsEndpoint
      .summary("Remove member")
      .delete
      .securityIn(authRequestParts)
      .in(path[CollectionId]("collectionId") / "members" / path[UserId]("memberId"))
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Collection not found")),
          oneOfVariant(
            StatusCode.PreconditionFailed,
            jsonBody[ErrorResponse].examples(List(notAMemberExample))
          )
        )
      )

  val endpoints: NonEmptyList[Endpoint[_, _, _, _, Any]] =
    NonEmptyList.of(
      listCollectionsEndpoint,
      getCollectionDetailsEndpoint,
      createCollectionEndpoint,
      deleteCollectionEndpoint,
      getSingleCollectionKeyEndpoint,
      getEncryptionKeysForAllCollectionsEndpoint,
      inviteCollectionMemberEndpoint,
      acceptInvitationEndpoint,
      cancelInvitationAcceptanceEndpoint,
      getCollectionMembersEndpoint,
      listPermissionsEndpoint,
      setPermissionsEndpoint,
      removeCollectionMemberEndpoint
    )

}
