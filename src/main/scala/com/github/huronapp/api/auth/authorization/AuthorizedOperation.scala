package com.github.huronapp.api.auth.authorization

import cats.Show
import cats.syntax.show._
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.domain.collections.CollectionId
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

final case class GetEncryptionKey(subject: Subject, collectionId: FUUID) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to read encryption key of collection $collectionId"

}

final case class CreateFile(subject: Subject, collectionId: CollectionId) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to create file in collection $collectionId"

}

final case class ReadMetadata(subject: Subject, collectionId: CollectionId) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to read metadata of objects from collection $collectionId"

}

final case class ReadContent(subject: Subject, collectionId: CollectionId) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to read content of objects from collection $collectionId"

}

final case class ModifyFile(subject: Subject, collectionId: CollectionId) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to modify objects in collection $collectionId"

}

final case class DeleteFile(subject: Subject, collectionId: CollectionId) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to delete objects from collection $collectionId"

}
