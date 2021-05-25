package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.domain.users.Language
import com.github.huronapp.api.domain.users.dto.fields.Nickname
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum

final case class PatchUserDataReq(nickName: Option[Nickname], language: Option[Language])

object PatchUserDataReq extends TapirCodecEnumeratum {

  implicit val codec: Codec[PatchUserDataReq] = deriveCodec[PatchUserDataReq]

  implicit val tapirSchema: Schema[PatchUserDataReq] = Schema.derived[PatchUserDataReq]

}
