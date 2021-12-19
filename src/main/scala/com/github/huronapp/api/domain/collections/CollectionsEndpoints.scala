package com.github.huronapp.api.domain.collections

import cats.data.NonEmptyList
import com.github.huronapp.api.auth.authentication.AuthenticationInputs
import com.github.huronapp.api.auth.authentication.TapirAuthenticationInputs.authRequestParts
import com.github.huronapp.api.domain.collections.dto.{CollectionData, EncryptionKeyData, NewCollectionReq}
import com.github.huronapp.api.http.{BaseEndpoint, ErrorResponse}
import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.FUUID
import sttp.model.StatusCode
import sttp.tapir.{Endpoint, PublicEndpoint}
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._

object CollectionsEndpoints extends BaseEndpoint {

  private val collectionsEndpoint: PublicEndpoint[Unit, Unit, Unit, Any] = publicApiEndpoint.tag("collections").in("collections")

  val listCollectionsEndpoint: Endpoint[AuthenticationInputs, Option[Boolean], ErrorResponse, List[CollectionData], Any] =
    collectionsEndpoint
      .summary("List collections available to user")
      .get
      .securityIn(authRequestParts)
      .in(query[Option[Boolean]]("onlyAccepted").description("Should return only collections accepted by user (default false)"))
      .out(jsonBody[List[CollectionData]])
      .errorOut(oneOf[ErrorResponse](unauthorized))

  val getCollectionDetailsEndpoint: Endpoint[AuthenticationInputs, FUUID, ErrorResponse, CollectionData, Any] =
    collectionsEndpoint
      .summary("Get collection details")
      .get
      .securityIn(authRequestParts)
      .in(path[FUUID]("collectionId"))
      .out(jsonBody[CollectionData])
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden
        )
      )

  val createCollectionEndpoint: Endpoint[AuthenticationInputs, NewCollectionReq, ErrorResponse, CollectionData, Any] =
    collectionsEndpoint
      .summary("Create new collection")
      .post
      .securityIn(authRequestParts)
      .in(jsonBody[NewCollectionReq])
      .out(jsonBody[CollectionData])
      .errorOut(oneOf[ErrorResponse](badRequest, unauthorized))

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

  val endpoints: NonEmptyList[Endpoint[_, _, _, _, Any]] =
    NonEmptyList.of(
      listCollectionsEndpoint,
      getCollectionDetailsEndpoint,
      createCollectionEndpoint,
      getSingleCollectionKeyEndpoint,
      getEncryptionKeysForAllCollectionsEndpoint
    )

}
