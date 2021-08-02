package com.github.huronapp.api.domain.users

import enumeratum._
import io.chrisdavenport.fuuid.FUUID

final case class KeyPair(id: FUUID, userId: FUUID, algorithm: KeyAlgorithm, publicKey: String, encryptedPrivateKey: String)

sealed trait KeyAlgorithm extends EnumEntry

object KeyAlgorithm extends Enum[KeyAlgorithm] with CirceEnum[KeyAlgorithm] {

  override def values = findValues

  case object Rsa extends KeyAlgorithm

}
