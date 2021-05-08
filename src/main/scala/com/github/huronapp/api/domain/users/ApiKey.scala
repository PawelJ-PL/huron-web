package com.github.huronapp.api.domain.users

import enumeratum._
import io.chrisdavenport.fuuid.FUUID

import java.time.Instant

final case class ApiKey(
  id: FUUID,
  key: String,
  userId: FUUID,
  keyType: ApiKeyType,
  description: String,
  enabled: Boolean,
  validTo: Option[Instant],
  createdAt: Instant,
  updatedAt: Instant)

sealed trait ApiKeyType extends EnumEntry

object ApiKeyType extends Enum[ApiKeyType] with CatsEnum[ApiKeyType] {

  override def values: IndexedSeq[ApiKeyType] = findValues

  case object Personal extends ApiKeyType

}
