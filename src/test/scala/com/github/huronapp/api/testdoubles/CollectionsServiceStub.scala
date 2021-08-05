package com.github.huronapp.api.testdoubles

import com.github.huronapp.api.constants.Collections
import com.github.huronapp.api.domain.collections.CollectionsService.CollectionsService
import com.github.huronapp.api.domain.collections.dto.NewCollectionReq
import com.github.huronapp.api.domain.collections.{Collection, CollectionsService, GetCollectionDetailsError}
import io.chrisdavenport.fuuid.FUUID
import zio.{ULayer, ZIO, ZLayer}

object CollectionsServiceStub extends Collections {

  final case class CollectionsServiceResponses(
    getAllCollections: ZIO[Any, Nothing, List[Collection]] = ZIO.succeed(List(ExampleCollection)),
    getCollectionDetails: ZIO[Any, GetCollectionDetailsError, Collection] = ZIO.succeed(ExampleCollection),
    createCollection: ZIO[Any, Nothing, Collection] = ZIO.succeed(ExampleCollection))

  def withResponses(responses: CollectionsServiceResponses): ULayer[CollectionsService] =
    ZLayer.succeed(new CollectionsService.Service {

      override def getAllCollectionsOfUser(userId: FUUID, onlyAccepted: Boolean): ZIO[Any, Nothing, List[Collection]] =
        responses.getAllCollections

      override def getCollectionDetailsAs(userId: FUUID, collectionId: FUUID): ZIO[Any, GetCollectionDetailsError, Collection] =
        responses.getCollectionDetails

      override def createCollectionAs(userId: FUUID, dto: NewCollectionReq): ZIO[Any, Nothing, Collection] = responses.createCollection

    })

}
