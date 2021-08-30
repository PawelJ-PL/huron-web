package com.github.huronapp.api.domain.files.dto

import com.github.huronapp.api.domain.files.dto.fields.FileName
import com.github.huronapp.api.utils.OptionalValue
import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class UpdateStorageUnitMetadataReq(parent: Option[OptionalValue[FUUID]], name: Option[FileName])

object UpdateStorageUnitMetadataReq {

  implicit val codec: Codec[UpdateStorageUnitMetadataReq] = deriveCodec

  implicit val tapirSchema: Schema[UpdateStorageUnitMetadataReq] = Schema.derived

}
