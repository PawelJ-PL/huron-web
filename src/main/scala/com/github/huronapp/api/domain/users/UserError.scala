package com.github.huronapp.api.domain.users

import cats.syntax.show._
import com.github.huronapp.api.domain.users.dto.PatchUserDataReq
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

final case class EmailAlreadyRegistered(emailDigest: String) extends CreateUserError {

  override val logMessage: String = show"Email with digest $emailDigest already registered"

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

final case class NoUpdates[A](resourceType: String, resourceId: FUUID, dto: A) extends PatchUserError with UpdateApiKeyError {

  override val logMessage: String = s"No updates provided for $resourceType $resourceId"

}

final case class UserNotFound(userId: FUUID) extends PatchUserError with UpdatePasswordError {

  override val logMessage: String = s"User with id $userId not found"

}

final case class PasswordsEqual(userId: FUUID) extends UpdatePasswordError {

  override val logMessage: String = s"New and old password for user $userId are equal"

}

final case class ApiKeyNotFound(keyId: FUUID) extends DeleteApiKeyError with UpdateApiKeyError {

  override val logMessage: String = s"API key with id $keyId not found"

}

final case class ApiKeyBelongsToAnotherUser(keyId: FUUID, expectedUser: FUUID, realUser: FUUID)
    extends DeleteApiKeyError
    with UpdateApiKeyError {

  override val logMessage: String = s"API key with ID $keyId belongs to user $realUser, not $expectedUser"

}
