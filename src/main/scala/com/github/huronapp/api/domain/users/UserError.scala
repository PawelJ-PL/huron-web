package com.github.huronapp.api.domain.users

import cats.syntax.show._
import com.github.huronapp.api.auth.authorization.{AuthorizationError => AuthError}
import io.chrisdavenport.fuuid.FUUID

sealed trait UserError {

  val logMessage: String

}

sealed trait CreateUserError extends UserError

sealed trait SignUpConfirmationError extends UserError

sealed trait CredentialsVerificationError extends UserError with UpdatePasswordError

sealed trait PatchUserError extends UserError

sealed trait UpdatePasswordError extends UserError

sealed trait RequestPasswordResetError extends UserError

sealed trait PasswordResetError extends UserError

sealed trait DeleteApiKeyError extends UserError

sealed trait UpdateApiKeyError extends UserError

sealed trait CreateContactError extends UserError

sealed trait EditContactError extends UserError

sealed trait GetKeyPairError extends UserError

final case class AuthorizationError(error: AuthError) extends UpdatePasswordError with GetKeyPairError {

  override val logMessage: String = error.message

}

final case class EmailAlreadyRegistered(emailDigest: String) extends CreateUserError {

  override val logMessage: String = show"Email with digest $emailDigest already registered"

}

final case class NickNameAlreadyRegistered(nickname: String) extends CreateUserError with PatchUserError {

  override val logMessage: String = show"Nickname $nickname already registered"

}

final case class NoValidTokenFound(token: String) extends SignUpConfirmationError with PasswordResetError {

  override val logMessage: String = s"Token $token is not valid"

}

final case class RegistrationAlreadyConfirmed(userId: FUUID) extends SignUpConfirmationError {

  override val logMessage: String = s"Registration for user was already confirmed"

}

final case class EmailNotFound(emailHash: String) extends CredentialsVerificationError with RequestPasswordResetError {

  override val logMessage: String = s"User with email hash $emailHash not found"

}

final case class UserIsNotActive(userId: FUUID)
    extends CredentialsVerificationError
    with PasswordResetError
    with RequestPasswordResetError {

  override val logMessage: String = s"User $userId is not active"

}

final case class InvalidPassword(emailHash: String) extends CredentialsVerificationError {

  override val logMessage: String = s"Invalid password for user with email hash $emailHash"

}

final case class NoUpdates[A](resourceType: String, resourceId: FUUID, dto: A)
    extends PatchUserError
    with UpdateApiKeyError
    with EditContactError {

  override val logMessage: String = s"No updates provided for $resourceType $resourceId"

}

final case class UserNotFound(userId: FUUID)
    extends PatchUserError
    with UpdatePasswordError
    with PasswordResetError
    with CreateContactError {

  override val logMessage: String = s"User with id $userId not found"

}

final case class PasswordsEqual(userId: FUUID) extends UpdatePasswordError {

  override val logMessage: String = s"New and old password for user $userId are equal"

}

final case class SomeEncryptionKeysMissingInUpdate(userId: FUUID, missingCollectionIds: Set[FUUID]) extends UpdatePasswordError {

  override val logMessage: String =
    s"Update doesn't contain encryption key for following collections: ${missingCollectionIds.mkString(", ")}"

}

final case class EncryptionKeyVersionMismatch(collectionId: FUUID, expectedVersion: FUUID, currentVersion: FUUID)
    extends UpdatePasswordError {

  override val logMessage: String =
    s"Trying to update encryption key for collection $collectionId with version $expectedVersion, but current version is $currentVersion"

}

final case class ApiKeyNotFound(keyId: FUUID) extends DeleteApiKeyError with UpdateApiKeyError {

  override val logMessage: String = s"API key with id $keyId not found"

}

final case class ApiKeyBelongsToAnotherUser(keyId: FUUID, expectedUser: FUUID, realUser: FUUID)
    extends DeleteApiKeyError
    with UpdateApiKeyError {

  override val logMessage: String = s"API key with ID $keyId belongs to user $realUser, not $expectedUser"

}

final case class EmailDigestDoesNotMatch(userId: FUUID, expectedDigest: String, currentDigest: String)
    extends PasswordResetError
    with UpdatePasswordError {

  override val logMessage: String =
    s"The digest of user $userId email address was expected to be $expectedDigest, but $currentDigest was found"

}

final case class ContactAliasAlreadyExists(userId: FUUID, alias: String, currentContact: FUUID)
    extends CreateContactError
    with EditContactError {

  override val logMessage: String = show"User $userId already has contact with alias $alias related to user $currentContact"

}

final case class ContactAlreadyExists(userId: FUUID, objectId: FUUID) extends CreateContactError {

  override val logMessage: String = show"User $userId has already saved contact with user $objectId"

}

final case class ForbiddenSelfToContacts(userId: FUUID) extends CreateContactError {

  override val logMessage: String = show"User $userId trying to add self to contacts"

}

final case class ContactNotFound(ownerId: FUUID, contactObjectId: FUUID) extends EditContactError {

  override val logMessage: String = show"User ${ownerId} has no contact with user ${contactObjectId}"

}
