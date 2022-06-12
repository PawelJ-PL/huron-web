package com.github.huronapp.api.domain.collections

import com.github.huronapp.api.http.ErrorResponse

object CollectionsErrorMapping {

  def getCollectionDetailsError(error: GetCollectionDetailsError): ErrorResponse =
    error match {
      case _: AuthorizationError => ErrorResponse.Forbidden("Operation not permitted")
      case _: CollectionNotFound => ErrorResponse.Forbidden("Operation not permitted")
    }

  def getEncryptionKeyError(error: GetEncryptionKeyError): ErrorResponse =
    error match {
      case _: AuthorizationError => ErrorResponse.Forbidden("Operation not permitted")
    }

  def getMembersError(error: GetMembersError): ErrorResponse =
    error match {
      case _: AuthorizationError => ErrorResponse.Forbidden("Operation not permitted")
    }

  def setMemberPermissionsError(error: SetMemberPermissionsError): ErrorResponse =
    error match {
      case _: AuthorizationError                             => ErrorResponse.Forbidden("Operation not permitted")
      case _: ChangeCollectionOwnerPermissionsNotAllowed     => ErrorResponse.Forbidden("The owner's permissions cannot be changed")
      case _: ChangeSelfPermissionsNotAllowed                => ErrorResponse.Forbidden("Changing own permissions is not allowed")
      case _: CollectionNotFound                             => ErrorResponse.NotFound("Collection not found")
      case UserIsNotMemberOfCollection(userId, collectionId) => CollectionsEndpoints.Responses.userNotAMember(userId, collectionId)
    }

  def deleteCollectionError(error: DeleteCollectionError): ErrorResponse =
    error match {
      case _: AuthorizationError => ErrorResponse.Forbidden("Operation not permitted")
      case _: CollectionNotEmpty => CollectionsEndpoints.Responses.collectionNotEmpty
    }

  def inviteMemberError(error: InviteMemberError): ErrorResponse =
    error match {
      case _: AuthorizationError                                                 => ErrorResponse.Forbidden("Operation not permitted")
      case _: CollectionNotFound                                                 => ErrorResponse.NotFound("Collection not found")
      case _: AlreadyMember                                                      => CollectionsEndpoints.Responses.alreadyMember
      case KeyVersionMismatch(collectionId, requestedKeyVersion, realKeyVersion) =>
        CollectionsEndpoints.Responses.keyVersionMismatch(collectionId, requestedKeyVersion, realKeyVersion)
      case _: UserNotFound                                                       => ErrorResponse.NotFound("User not found")
    }

  def removeMemberError(error: RemoveMemberError): ErrorResponse =
    error match {
      case _: AuthorizationError                             => ErrorResponse.Forbidden("Operation not permitted")
      case _: RemoveCollectionOwnerNotAllowed                => ErrorResponse.Forbidden("Collections owner can't be removed")
      case _: CollectionNotFound                             => ErrorResponse.NotFound("Collection not found")
      case UserIsNotMemberOfCollection(userId, collectionId) => CollectionsEndpoints.Responses.userNotAMember(userId, collectionId)
    }

  def acceptInvitationError(error: AcceptInvitationError): ErrorResponse =
    error match {
      case _: InvitationNotFound        => ErrorResponse.NotFound("Invitation not found")
      case _: InvitationAlreadyAccepted => CollectionsEndpoints.Responses.invitationAlreadyAccepted
    }

  def cancelInvitationAcceptanceError(error: CancelInvitationAcceptanceError): ErrorResponse =
    error match {
      case _: InvitationNotFound    => ErrorResponse.NotFound("Invitation not found")
      case _: InvitationNotAccepted => CollectionsEndpoints.Responses.invitationNotAccepted
    }

  def listMemberPermissionsError(error: ListMemberPermissionsError): ErrorResponse =
    error match {
      case _: AuthorizationError => ErrorResponse.Forbidden("Operation not permitted")
    }

}
