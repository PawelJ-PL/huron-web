package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.domain.users.KeyAlgorithm
import com.github.huronapp.api.domain.users.dto.fields.PublicKey
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum

case class PublicKeyResp(algorithm: KeyAlgorithm, publicKey: PublicKey)

object PublicKeyResp extends TapirCodecEnumeratum {

  implicit val codec: Codec[PublicKeyResp] = deriveCodec[PublicKeyResp]

  implicit val tapirSchema: Schema[PublicKeyResp] = Schema.derived[PublicKeyResp]

}
