package com.github.huronapp.api.domain.users

import enumeratum._
import io.chrisdavenport.fuuid.FUUID

final case class TemporaryToken(token: String, userId: FUUID, tokenType: TokenType)

sealed trait TokenType extends EnumEntry

object TokenType extends Enum[TokenType] with CatsEnum[TokenType] {

  override def values: IndexedSeq[TokenType] = findValues

  case object Registration extends TokenType

  case object PasswordReset extends TokenType

}
