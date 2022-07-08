package com.github.huronapp.api.auth.authorization

import cats.Show
import cats.syntax.show._
import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.domain.collections.CollectionId
import com.github.huronapp.api.domain.users.UserId
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

final case class GetKeyPair(subject: Subject, keyPairOwner: UserId) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to get keypair of user $keyPairOwner"

}

final case class SetEncryptionKey(subject: Subject, collectionId: FUUID, userId: FUUID) extends AuthorizedOperation {

  override val failureMessage: String =
    show"Subject $subject not authorized to set encryption key for collection $collectionId of user $userId"

}

final case class GetEncryptionKey(subject: Subject, collectionId: FUUID) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to read encryption key of collection $collectionId"

}

final case class InviteCollectionMember(subject: Subject, collectionId: CollectionId, memberId: UserId) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to add member to collection $collectionId"

}

final case class GetCollectionMembers(subject: Subject, collectionId: CollectionId) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to list members of collection $collectionId"

}

final case class SetCollectionPermissions(subject: Subject, collectionId: CollectionId, memberId: UserId) extends AuthorizedOperation {

  override val failureMessage: String =
    show"Subject $subject not authorized to assign permissions of collection ${collectionId} to user ${memberId}"

}

final case class RemoveCollectionMember(subject: Subject, collectionId: CollectionId, memberId: UserId) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to remove user from collection $collectionId"

}

final case class DeleteCollection(subject: Subject, collectionId: CollectionId) extends AuthorizedOperation {

  override val failureMessage: String = show"Subject $subject not authorized to delete collection $collectionId"

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

final case class ListMemberPermissions(subject: Subject, collectionId: CollectionId, memberId: UserId) extends AuthorizedOperation {

  override val failureMessage: String =
    show"Subject $subject is not authorized to list permissions of user $memberId to collection $collectionId"

}
