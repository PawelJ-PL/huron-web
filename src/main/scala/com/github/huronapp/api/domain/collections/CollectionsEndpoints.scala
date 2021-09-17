package com.github.huronapp.api.domain.collections

import cats.data.NonEmptyList
import com.github.huronapp.api.auth.authentication.AuthenticationInputs
import com.github.huronapp.api.auth.authentication.TapirAuthenticationInputs.authRequestParts
import com.github.huronapp.api.domain.collections.dto.{CollectionData, EncryptionKeyData, NewCollectionReq}
import com.github.huronapp.api.http.{BaseEndpoint, ErrorResponse}
import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.FUUID
import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.model.StatusCode
import sttp.tapir.Endpoint
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._

object CollectionsEndpoints extends BaseEndpoint {

  private val collectionsEndpoint: ZEndpoint[Unit, Unit, Unit] = apiEndpoint.tag("collections").in("collections")

  val listCollectionsEndpoint
    : Endpoint[(AuthenticationInputs, Option[Boolean]), ErrorResponse, List[CollectionData], ZioStreams with capabilities.WebSockets] =
    collectionsEndpoint
      .summary("List collections available to user")
      .get
      .prependIn(authRequestParts)
      .in(query[Option[Boolean]]("onlyAccepted").description("Should return only collections accepted by user (default false)"))
      .out(jsonBody[List[CollectionData]])
      .errorOut(oneOf[ErrorResponse](unauthorized))

  val getCollectionDetailsEndpoint
    : Endpoint[(AuthenticationInputs, FUUID), ErrorResponse, CollectionData, ZioStreams with capabilities.WebSockets] = collectionsEndpoint
    .summary("Get collection details")
    .get
    .prependIn(authRequestParts)
    .in(path[FUUID]("collectionId"))
    .out(jsonBody[CollectionData])
    .errorOut(
      oneOf[ErrorResponse](
        badRequest,
        unauthorized,
        forbidden
      )
    )

  val createCollectionEndpoint
    : Endpoint[(AuthenticationInputs, NewCollectionReq), ErrorResponse, CollectionData, ZioStreams with capabilities.WebSockets] =
    collectionsEndpoint
      .summary("Create new collection")
      .post
      .prependIn(authRequestParts)
      .in(jsonBody[NewCollectionReq])
      .out(jsonBody[CollectionData])
      .errorOut(oneOf[ErrorResponse](badRequest, unauthorized))

  val getSingleCollectionKeyEndpoint
    : Endpoint[(AuthenticationInputs, FUUID), ErrorResponse, EncryptionKeyData, ZioStreams with capabilities.WebSockets] =
    collectionsEndpoint
      .summary("Get encryption key for collection")
      .get
      .prependIn(authRequestParts)
      .in(path[FUUID]("collectionId") / "encryption-key")
      .out(jsonBody[EncryptionKeyData])
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          forbidden,
          oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Key not set for collection"))
        )
      )

  val getEncryptionKeysForAllCollectionsEndpoint
    : Endpoint[AuthenticationInputs, ErrorResponse, List[EncryptionKeyData], ZioStreams with capabilities.WebSockets] =
    collectionsEndpoint
      .summary("Get encryption keys for all collections")
      .get
      .prependIn(authRequestParts)
      .in("encryption-key")
      .out(jsonBody[List[EncryptionKeyData]])
      .errorOut(oneOf[ErrorResponse](unauthorized))

  val endpoints: NonEmptyList[ZEndpoint[_, _, _]] =
    NonEmptyList.of(
      listCollectionsEndpoint,
      getCollectionDetailsEndpoint,
      createCollectionEndpoint,
      getSingleCollectionKeyEndpoint,
      getEncryptionKeysForAllCollectionsEndpoint
    )

}
