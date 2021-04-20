package com.github.huronapp.api.testdoubles

import com.github.huronapp.api.constants.Users
import com.github.huronapp.api.domain.users.UsersService.UsersService
import com.github.huronapp.api.domain.users.dto.{NewUserReq, PasswordResetReq, PatchUserDataReq, UpdatePasswordReq}
import com.github.huronapp.api.domain.users.{
  CreateUserError,
  CredentialsVerificationError,
  Email,
  PasswordResetError,
  PatchUserError,
  RequestPasswordResetError,
  SignUpConfirmationError,
  TemporaryToken,
  TokenType,
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
    passwordReset: ZIO[Any, PasswordResetError, Unit] = ZIO.unit)

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

    })

}
