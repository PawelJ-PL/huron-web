package com.github.huronapp.api.domain.collections

import cats.syntax.show._
import com.github.huronapp.api.auth.authorization.{AuthorizationError => AuthError}
import com.github.huronapp.api.domain.users.UserId
import io.chrisdavenport.fuuid.FUUID

sealed trait CollectionError {

  val logMessage: String

}

sealed trait GetCollectionDetailsError extends CollectionError

sealed trait GetEncryptionKeyError extends CollectionError

sealed trait GetMembersError extends CollectionError

sealed trait SetMemberPermissionsError extends CollectionError

sealed trait DeleteCollectionError extends CollectionError

sealed trait InviteMemberError extends CollectionError

sealed trait RemoveMemberError extends CollectionError

sealed trait AcceptInvitationError extends CollectionError

sealed trait CancelInvitationAcceptanceError extends CollectionError

sealed trait ListMemberPermissionsError extends CollectionError

final case class AuthorizationError(error: AuthError)
    extends GetCollectionDetailsError
    with GetEncryptionKeyError
    with GetMembersError
    with SetMemberPermissionsError
    with DeleteCollectionError
    with InviteMemberError
    with RemoveMemberError
    with ListMemberPermissionsError {

  override val logMessage: String = error.message

}

final case class CollectionNotFound(collectionId: FUUID)
    extends GetCollectionDetailsError
    with SetMemberPermissionsError
    with InviteMemberError
    with RemoveMemberError {

  override val logMessage: String = show"Collection $collectionId not found"

}

final case class UserIsNotMemberOfCollection(userId: UserId, collectionId: CollectionId)
    extends SetMemberPermissionsError
    with RemoveMemberError {

  override val logMessage: String =
    show"User $userId is not member of collection $collectionId"

}

final case class ChangeSelfPermissionsNotAllowed(userId: UserId) extends SetMemberPermissionsError {

  override val logMessage: String = show"User $userId is going to change self permissions"

}

final case class ChangeCollectionOwnerPermissionsNotAllowed(userId: UserId, ownerId: UserId, collectionId: CollectionId)
    extends SetMemberPermissionsError {

  override val logMessage: String =
    show"User $userId is trying to change permissions of user $ownerId who is an owner of collection $collectionId"

}

final case class CollectionNotEmpty(collectionId: CollectionId) extends DeleteCollectionError {

  override val logMessage: String = show"Unable to delete collection $collectionId which is not empty"

}

final case class AlreadyMember(memberId: UserId, collectionId: CollectionId) extends InviteMemberError {

  override val logMessage: String = show"User $memberId is already member of collection $collectionId"

}

final case class KeyVersionMismatch(collectionId: CollectionId, requestedKeyVersion: FUUID, realKeyVersion: FUUID)
    extends InviteMemberError {

  override val logMessage: String =
    show"Trying to invite new member with collection key ID $requestedKeyVersion but current key ID is $realKeyVersion"

}

final case class UserNotFound(userId: UserId) extends InviteMemberError {

  override val logMessage: String = show"User $userId does not exist"

}

final case class RemoveCollectionOwnerNotAllowed(userId: UserId, collectionId: CollectionId, owner: UserId) extends RemoveMemberError {

  override val logMessage: String = show"User $userId is going to remove member $owner who is owner of collection $collectionId"

}

final case class InvitationNotFound(collectionId: CollectionId, userId: UserId)
    extends AcceptInvitationError
    with CancelInvitationAcceptanceError {

  override val logMessage: String = show"Invitation to collection $collectionId not found for user $userId"

}

final case class InvitationAlreadyAccepted(collectionId: CollectionId, userId: UserId) extends AcceptInvitationError {

  override val logMessage: String = show"Invitation to collection $collectionId was already accepted by user $userId"

}

final case class InvitationNotAccepted(collectionId: CollectionId, userId: UserId) extends CancelInvitationAcceptanceError {

  override val logMessage: String = show"Collection ${collectionId} was not accepted yet by user ${userId}"

}
