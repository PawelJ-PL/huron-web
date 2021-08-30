package com.github.huronapp.api.domain

import cats.{Eq, Show}
import io.chrisdavenport.fuuid.FUUID
import io.estatico.newtype.macros.newtype
package object collections {

  @newtype
  final case class CollectionId(id: FUUID)

  object CollectionId {

    implicit val show: Show[CollectionId] = Show.show(_.id.show)

    implicit val eq: Eq[CollectionId] = Eq.by(_.id)

  }

}
