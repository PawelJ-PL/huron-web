package com.github.huronapp.api.auth.authorization

import cats.Show
import cats.syntax.show._
import com.github.huronapp.api.auth.authorization.types.Subject
import io.chrisdavenport.fuuid.FUUID
import io.estatico.newtype.macros.newtype

object types {

  @newtype
  final case class Subject(userId: FUUID)

  object Subject {

    implicit val show: Show[Subject] = Show.show(_.userId.toString())

  }

}

sealed trait AuthorizedOperation {

  val failureMessage: String

}

final case class GetCollectionDetails(subject: Subject, collectionId: FUUID) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to read details of collection $collectionId"

}

final case class SetEncryptionKey(subject: Subject, collectionId: FUUID, userId: FUUID) extends AuthorizedOperation {

  override val failureMessage: String =
    show"Subject $subject not authorized to set encryption key for collection $collectionId of user $userId"

}
