package com.github.huronapp.api.domain.collections.dto

import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import com.github.huronapp.api.domain.collections.CollectionPermission
import com.github.huronapp.api.domain.collections.dto.fields.EncryptedCollectionKey
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import com.github.huronapp.api.utils.Implicits.nonEmptyList._
import com.github.huronapp.api.utils.Implicits.collectionPermission._
import sttp.tapir.codec.refined._

final case class NewMemberReq(
  collectionKeyVersion: FUUID,
  encryptedCollectionKey: EncryptedCollectionKey,
  permissions: Refined[List[CollectionPermission], NonEmpty])

object NewMemberReq {

  implicit val codec: Codec[NewMemberReq] = deriveCodec[NewMemberReq]

  implicit val tapirSchema: Schema[NewMemberReq] = Schema.derived[NewMemberReq]

}
