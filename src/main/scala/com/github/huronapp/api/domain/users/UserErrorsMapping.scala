package com.github.huronapp.api.domain.users

import com.github.huronapp.api.http.ErrorResponse

object UserErrorsMapping {

  def createUserError(error: CreateUserError): ErrorResponse =
    error match {
      case _: EmailAlreadyRegistered => ErrorResponse.Conflict("Email already registered")
    }

  def confirmRegistrationError(error: SignUpConfirmationError): ErrorResponse =
    error match {
      case _: NoValidTokenFound            => ErrorResponse.NotFound("Invalid token")
      case _: RegistrationAlreadyConfirmed => ErrorResponse.NotFound("Invalid token")
    }

  def loginError(error: CredentialsVerificationError): ErrorResponse =
    error match {
      case _: EmailNotFound   => ErrorResponse.Unauthorized("Invalid credentials")
      case _: UserIsNotActive => ErrorResponse.Unauthorized("Invalid credentials")
      case _: InvalidPassword => ErrorResponse.Unauthorized("Invalid credentials")
    }

  def patchUserError(error: PatchUserError): ErrorResponse =
    error match {
      case _: NoUpdates[_] => ErrorResponse.BadRequest("No updates in request")
      case _: UserNotFound => ErrorResponse.NotFound("User not found")
    }

  def updatePasswordError(error: UpdatePasswordError): ErrorResponse =
    error match {
      case _: CredentialsVerificationError                               => UsersEndpoints.Responses.updatePasswordInvalidCredentials
      case _: EmailDigestDoesNotMatch                                    => UsersEndpoints.Responses.updatePasswordInvalidEmail
      case _: UserNotFound                                               => ErrorResponse.NotFound("User not found")
      case _: PasswordsEqual                                             => UsersEndpoints.Responses.updatePasswordPasswordsEquals
      case SomeEncryptionKeysMissingInUpdate(_, missingCollectionIds)    =>
        UsersEndpoints.Responses.updatePasswordMissingEncryptionKeys(missingCollectionIds)
      case _: AuthorizationError                                         => ErrorResponse.Forbidden("Action not allowed")
      case EncryptionKeyVersionMismatch(collectionId, _, currentVersion) =>
        UsersEndpoints.Responses.updatePasswordKeyVersionMismatch(collectionId, currentVersion)
    }

  def deleteApiKeyError(error: DeleteApiKeyError): ErrorResponse =
    error match {
      case ApiKeyNotFound(keyId)                   => ErrorResponse.NotFound(s"API key $keyId not found")
      case ApiKeyBelongsToAnotherUser(keyId, _, _) => ErrorResponse.NotFound(s"API key $keyId not found")
    }

  def updateApiKeyError(error: UpdateApiKeyError): ErrorResponse =
    error match {
      case _: NoUpdates[_]                         => ErrorResponse.BadRequest("No updates in request")
      case ApiKeyNotFound(keyId)                   => ErrorResponse.NotFound(s"API key $keyId not found")
      case ApiKeyBelongsToAnotherUser(keyId, _, _) => ErrorResponse.NotFound(s"API key $keyId not found")
    }

}
