package com.github.huronapp.api.testdoubles

import com.github.huronapp.api.constants.Users
import com.github.huronapp.api.domain.users.UsersService.UsersService
import com.github.huronapp.api.domain.users.dto.{
  NewPersonalApiKeyReq,
  NewUserReq,
  PasswordResetReq,
  PatchUserDataReq,
  UpdateApiKeyDataReq,
  UpdatePasswordReq
}
import com.github.huronapp.api.domain.users.{
  ApiKey,
  ApiKeyType,
  CreateUserError,
  CredentialsVerificationError,
  DeleteApiKeyError,
  Email,
  PasswordResetError,
  PatchUserError,
  RequestPasswordResetError,
  SignUpConfirmationError,
  TemporaryToken,
  TokenType,
  UpdateApiKeyError,
  UpdatePasswordError,
  User,
  UsersService
}
import io.chrisdavenport.fuuid.FUUID
import zio.{ULayer, ZIO, ZLayer}

object UsersServiceStub extends Users {

  final case class UsersServiceResponses(
    createUser: ZIO[Any, CreateUserError, User] = ZIO.succeed(ExampleUser),
    confirmRegistration: ZIO[Any, SignUpConfirmationError, FUUID] = ZIO.succeed(ExampleUserId),
    login: ZIO[Any, CredentialsVerificationError, User] = ZIO.succeed(ExampleUser),
    userData: ZIO[Any, Nothing, Option[User]] = ZIO.some(ExampleUser),
    patchUser: ZIO[Any, PatchUserError, User] = ZIO.succeed(ExampleUser),
    updatePassword: ZIO[Any, UpdatePasswordError, Unit] = ZIO.unit,
    passwordResetRequest: ZIO[Any, RequestPasswordResetError, TemporaryToken] = ZIO.succeed(TemporaryToken("abc", ExampleUserId,
        TokenType.PasswordReset)),
    passwordReset: ZIO[Any, PasswordResetError, Unit] = ZIO.unit,
    createApiKey: ZIO[Any, Nothing, ApiKey] = ZIO.succeed(ExampleApiKey),
    listApiKeys: ZIO[Any, Nothing, List[ApiKey]] = ZIO.succeed(List(ExampleApiKey)),
    deleteApiKey: ZIO[Any, DeleteApiKeyError, Unit] = ZIO.unit,
    updateApiKey: ZIO[Any, UpdateApiKeyError, ApiKey] = ZIO.succeed(ExampleApiKey))

  def withResponses(responses: UsersServiceResponses): ULayer[UsersService] =
    ZLayer.succeed(new UsersService.Service {

      override def createUser(dto: NewUserReq): ZIO[Any, CreateUserError, User] = responses.createUser

      override def confirmRegistration(token: String): ZIO[Any, SignUpConfirmationError, FUUID] = responses.confirmRegistration

      override def verifyCredentials(email: Email, password: String): ZIO[Any, CredentialsVerificationError, User] = responses.login

      override def userData(userId: FUUID): ZIO[Any, Nothing, Option[User]] = responses.userData

      override def patchUserData(userId: FUUID, dto: PatchUserDataReq): ZIO[Any, PatchUserError, User] = responses.patchUser

      override def updatePasswordForUser(userId: FUUID, dto: UpdatePasswordReq): ZIO[Any, UpdatePasswordError, Unit] =
        responses.updatePassword

      override def requestPasswordResetForUser(email: Email): ZIO[Any, RequestPasswordResetError, TemporaryToken] =
        responses.passwordResetRequest

      override def passwordResetUsingToken(tokenValue: String, dto: PasswordResetReq): ZIO[Any, PasswordResetError, Unit] =
        responses.passwordReset

      override def createApiKeyForUser(userId: FUUID, dto: NewPersonalApiKeyReq): ZIO[Any, Nothing, ApiKey] = responses.createApiKey

      override def getApiKeysOf(userId: FUUID, keyType: ApiKeyType): ZIO[Any, Nothing, List[ApiKey]] = responses.listApiKeys

      override def deleteApiKeyAs(userId: FUUID, keyId: FUUID): ZIO[Any, DeleteApiKeyError, Unit] = responses.deleteApiKey

      override def updateApiKeyAs(userId: FUUID, keyId: FUUID, dto: UpdateApiKeyDataReq): ZIO[Any, UpdateApiKeyError, ApiKey] = responses.updateApiKey

    })

}
