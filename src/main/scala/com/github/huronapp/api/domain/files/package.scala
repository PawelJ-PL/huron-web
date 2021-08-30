package com.github.huronapp.api.domain

import cats.{Eq, Show}
import io.chrisdavenport.fuuid.FUUID
import io.estatico.newtype.macros.newtype

package object files {

  @newtype
  final case class FileId(id: FUUID)

  object FileId {

    implicit val show: Show[FileId] = Show.show(_.id.show)

    implicit val eq: Eq[FileId] = Eq.by(_.id)

  }

  @newtype
  final case class FileVersionId(id: FUUID)

  object FileVersionId {

    implicit val eq: Eq[FileVersionId] = Eq.by(_.id)

    implicit val show: Show[FileVersionId] = Show.show(_.id.show)

  }

}
