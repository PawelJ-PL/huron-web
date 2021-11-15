package com.github.huronapp.api.domain

import cats.Show
import io.chrisdavenport.fuuid.FUUID
import io.estatico.newtype.macros.newtype

package object users {

  @newtype
  final case class UserId(id: FUUID)

  object UserId {

    implicit val show: Show[UserId] = Show.show(_.id.show)

  }

  type UserWithContact = (User, Option[UserContact])

  type ContactWithUser = (UserContact, User)

}
