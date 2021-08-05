package com.github.huronapp.api.domain.users.dto

import com.github.huronapp.api.domain.collections.dto.EncryptionKeyData
import com.github.huronapp.api.domain.users.Email
import com.github.huronapp.api.domain.users.dto.fields.{KeyPair, Password}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class UpdatePasswordReq(
  email: Email,
  currentPassword: String,
  newPassword: Password,
  keyPair: KeyPair,
  collectionEncryptionKeys: List[EncryptionKeyData])

object UpdatePasswordReq {

  implicit val codec: Codec[UpdatePasswordReq] = deriveCodec[UpdatePasswordReq]

  implicit val tapirSchema: Schema[UpdatePasswordReq] = Schema.derived[UpdatePasswordReq]

}
