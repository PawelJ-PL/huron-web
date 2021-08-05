package com.github.huronapp.api.domain.collections

import cats.data.NonEmptyList
import com.github.huronapp.api.auth.authentication.AuthenticationInputs
import com.github.huronapp.api.auth.authentication.TapirAuthenticationInputs.authRequestParts
import com.github.huronapp.api.domain.collections.dto.{CollectionData, NewCollectionReq}
import com.github.huronapp.api.http.{BaseEndpoint, ErrorResponse}
import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.FUUID
import sttp.model.StatusCode
import sttp.tapir.Endpoint
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._

object CollectionsEndpoints extends BaseEndpoint {

  private val collectionsEndpoint: ZEndpoint[Unit, Unit, Unit] = apiEndpoint.tag("collections").in("collections")

  val listCollectionsEndpoint: Endpoint[(AuthenticationInputs, Option[Boolean]), ErrorResponse, List[CollectionData], Any] =
    collectionsEndpoint
      .summary("List collections available to user")
      .get
      .prependIn(authRequestParts)
      .in(query[Option[Boolean]]("onlyAccepted").description("Should return only collections accepted by user (default false)"))
      .out(jsonBody[List[CollectionData]])
      .errorOut(oneOf[ErrorResponse](unauthorized))

  val getCollectionDetailsEndpoint: Endpoint[(AuthenticationInputs, FUUID), ErrorResponse, CollectionData, Any] = collectionsEndpoint
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

  val createCollectionEndpoint: Endpoint[(AuthenticationInputs, NewCollectionReq), ErrorResponse, CollectionData, Any] = collectionsEndpoint
    .summary("Create new collection")
    .post
    .prependIn(authRequestParts)
    .in(jsonBody[NewCollectionReq])
    .out(jsonBody[CollectionData])
    .errorOut(oneOf[ErrorResponse](badRequest, unauthorized))

  val endpoints: NonEmptyList[ZEndpoint[_, _, _]] =
    NonEmptyList.of(listCollectionsEndpoint, getCollectionDetailsEndpoint, createCollectionEndpoint)

}
