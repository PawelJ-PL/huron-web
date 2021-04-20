package com.github.huronapp.api.domain.users

import enumeratum._
import io.chrisdavenport.fuuid.FUUID

final case class User(id: FUUID, emailHash: String, nickName: String, language: Language)

sealed trait Language extends EnumEntry

object Language extends Enum[Language] with CirceEnum[Language] with CatsEnum[Language] {

  override def values = findValues

  case object En extends Language

  case object Pl extends Language

}
