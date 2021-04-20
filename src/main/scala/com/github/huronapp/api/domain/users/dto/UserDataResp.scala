package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.domain.users.Language
import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum

final case class UserDataResp(id: FUUID, nickName: String, language: Language)

object UserDataResp extends TapirCodecEnumeratum {

  implicit val codec: Codec[UserDataResp] = deriveCodec[UserDataResp]

  implicit val tapirSchema: Schema[UserDataResp] = Schema.derived[UserDataResp]

}
