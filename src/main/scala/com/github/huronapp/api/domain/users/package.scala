package com.github.huronapp.api.domain

import cats.{Eq, Show}
import cats.syntax.eq._
import io.chrisdavenport.fuuid.FUUID
import io.estatico.newtype.macros.newtype

package object users {

  @newtype
  final case class UserId(id: FUUID)

  object UserId {

    implicit val show: Show[UserId] = Show.show(_.id.show)

    implicit val eq: Eq[UserId] = Eq.instance(_.id === _.id)

  }

  type UserWithContact = (User, Option[UserContact])

  type ContactWithUser = (UserContact, User)

}
