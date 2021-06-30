package com.github.huronapp.api.domain.users

import com.github.huronapp.api.constants.{Config, MiscConstants, Users}
import com.github.huronapp.api.domain.users.UsersService.UsersService
import com.github.huronapp.api.domain.users.dto.fields.{ApiKeyDescription, Nickname, Password}
import com.github.huronapp.api.domain.users.dto.{
  NewPersonalApiKeyReq,
  NewUserReq,
  PasswordResetReq,
  PatchUserDataReq,
  UpdateApiKeyDataReq,
  UpdatePasswordReq
}
import com.github.huronapp.api.messagebus.InternalMessage
import com.github.huronapp.api.testdoubles.{CryptoStub, InternalTopicFake, KamonTracingFake, RandomUtilsStub, UsersRepoFake}
import com.github.huronapp.api.utils.OptionalValue
import io.github.gaelrenoux.tranzactio.doobie.Database
import kamon.context.Context
import zio.blocking.Blocking
import zio.{Ref, ZLayer}
import zio.logging.slf4j.Slf4jLogger
import zio.test.Assertion.{equalTo, hasSameElements, isEmpty, isLeft, isNone, isSome}
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

import java.time.Instant

object UserServiceSpec extends DefaultRunnableSpec with Users with Config with MiscConstants {

  private val logger = Slf4jLogger.make((_, str) => str)

  private def createUsersService(
    usersRepoRef: Ref[UsersRepoFake.UsersRepoState],
    internalTopic: Ref[List[InternalMessage]]
  ): ZLayer[Blocking, Nothing, UsersService] =
    CryptoStub.create ++ UsersRepoFake.create(usersRepoRef) ++ Database.none ++ RandomUtilsStub.create ++ logger ++ InternalTopicFake
      .usingRef(internalTopic) ++ ZLayer.succeed(ExampleSecurityConfig) ++ KamonTracingFake.noOp >>> UsersService.live

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Users service suite")(
      createUser,
      createUserWithEmailConflict,
      confirmSignUp,
      confirmSignUpWithInvalidToken,
      confirmAlreadyConfirmedRegistration,
      verifyCredentials,
      verifyCredentialsForNonExistingEmail,
      verifyCredentialsForInvalidPassword,
      verifyCredentialsForInactiveUser,
      getUserData,
      getUserDataForNonExistingUser,
      updateUserData,
      updateUserDataWithNoUpdates,
      updateUserDataWithMissingUser,
      updatePassword,
      updatePasswordWithMissingEmail,
      updatePasswordWithInvalidCredentials,
      updatePasswordWithInvalidEmail,
      updatePasswordWithTheSameValue,
      updatePasswordForInactiveUser,
      updatePasswordForNonExistingUser,
      passwordResetRequest,
      passwordResetRequestForNonExistingEmail,
      passwordResetRequestForInactiveUser,
      passwordResetWithToken,
      passwordResetWithTokenForInactiveUser,
      passwordResetWithTokenForInvalidEmail,
      createApiKey,
      getApiKeys,
      deleteApiKey,
      deleteNonExistingApiKey,
      deleteApiKeyOwnedByAnotherUser,
      updateApiKey,
      updateApiKeyWithEmptyUpdatesSet,
      updateNonExistingApiKey,
      updateApiKeyOwnedByAnotherUser
    )

  private val newUserDto =
    NewUserReq(Nickname(ExampleUserNickName), ExampleUserEmail, Password(ExampleUserPassword), Some(ExampleUserLanguage))

  private val createUser = testM("should successfully create a user") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo           <- Ref.make(UsersRepoFake.UsersRepoState())
      user                <- UsersService.createUser(newUserDto).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      expectedUser = User(FirstRandomFuuid, "digest(alice@example.org)", ExampleUserNickName, Language.Pl)
      expectedToken = "000102030405060708090a0b0c0d0e0f1011121314151617"
      sentMessages        <- internalTopic.get
    } yield assert(user)(equalTo(expectedUser)) &&
      assert(finalUsersRepoState.users)(hasSameElements(Set(expectedUser))) &&
      assert(finalUsersRepoState.auth)(
        hasSameElements(Set(UserAuth(FirstRandomFuuid, "bcrypt(secret-password)", confirmed = false, enabled = true)))
      ) &&
      assert(finalUsersRepoState.tokens)(hasSameElements(Set(TemporaryToken(expectedToken, FirstRandomFuuid, TokenType.Registration)))) &&
      assert(sentMessages)(
        hasSameElements(List(InternalMessage.UserRegistered(expectedUser, ExampleUserEmail, expectedToken, Some(Context.Empty))))
      )
  }

  private val createUserWithEmailConflict = testM("should not create user if email registered") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser.copy(emailHash = "digest(alice@example.org)")))
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.createUser(newUserDto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(EmailAlreadyRegistered("digest(alice@example.org)")))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val confirmSignUp = testM("should confirm registration") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             auth = Set(UserAuth(ExampleUserId, "passHash", confirmed = false, enabled = true)),
                             tokens = Set(TemporaryToken("abc", ExampleUserId, TokenType.Registration))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      userId              <- UsersService.confirmRegistration("abc").provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(userId)(equalTo(ExampleUserId)) &&
      assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(
        hasSameElements(Set(UserAuth(ExampleUserId, "passHash", confirmed = true, enabled = true)))
      ) &&
      assert(finalUsersRepoState.tokens)(isEmpty) &&
      assert(sentMessages)(isEmpty)
  }

  private val confirmSignUpWithInvalidToken = testM("should not confirm registration if token is invalid") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(auth = Set(UserAuth(ExampleUserId, "passHash", confirmed = false, enabled = true)))
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.confirmRegistration("abc").provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(NoValidTokenFound("abc")))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val confirmAlreadyConfirmedRegistration = testM("should not confirm registration if already confirmed") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             auth = Set(UserAuth(ExampleUserId, "passHash", confirmed = true, enabled = true)),
                             tokens = Set(TemporaryToken("abc", ExampleUserId, TokenType.Registration))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.confirmRegistration("abc").provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(RegistrationAlreadyConfirmed(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val verifyCredentials = testM("should verify valid credentials") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      user                <-
        UsersService.verifyCredentials(ExampleUserEmail, ExampleUserPassword).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(user)(equalTo(ExampleUser)) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val verifyCredentialsForNonExistingEmail = testM("should fail on credentials validation if email not found") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService
                               .verifyCredentials(Email("foo@bar"), ExampleUserPassword)
                               .provideLayer(createUsersService(usersRepo, internalTopic))
                               .either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(EmailNotFound("digest(foo@bar)")))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val verifyCredentialsForInvalidPassword = testM("should fail on credentials validation if password is incorrect") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService
                               .verifyCredentials(ExampleUserEmail, "invalid-password")
                               .provideLayer(createUsersService(usersRepo, internalTopic))
                               .either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(InvalidPassword(ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val verifyCredentialsForInactiveUser = testM("should fail on credentials validation if user is not active") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = false))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService
                               .verifyCredentials(ExampleUserEmail, ExampleUserPassword)
                               .provideLayer(createUsersService(usersRepo, internalTopic))
                               .either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(UserIsNotActive(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val getUserData = testM("should get users data") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser))
      usersRepo           <- Ref.make(initUsersRepoState)
      user                <- UsersService.userData(ExampleUserId).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(user)(isSome(equalTo(ExampleUser))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val getUserDataForNonExistingUser = testM("should get None if user not exists") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState()
      usersRepo           <- Ref.make(initUsersRepoState)
      user                <- UsersService.userData(ExampleUserId).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(user)(isNone) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updateUserData = testM("should update user data") {
    val dto = PatchUserDataReq(nickName = Some(Nickname("Other name")), language = None)

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser))
      usersRepo           <- Ref.make(initUsersRepoState)
      user                <- UsersService.patchUserData(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(user)(equalTo(ExampleUser.copy(nickName = "Other name"))) &&
      assert(finalUsersRepoState.users)(equalTo(Set(ExampleUser.copy(nickName = "Other name")))) &&
      assert(finalUsersRepoState.auth)(equalTo(initUsersRepoState.auth)) &&
      assert(finalUsersRepoState.tokens)(equalTo(initUsersRepoState.tokens)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updateUserDataWithNoUpdates = testM("should not update user data if no updates provided") {
    val dto = PatchUserDataReq(nickName = None, language = None)

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser))
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.patchUserData(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(NoUpdates("user", ExampleUserId, dto)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updateUserDataWithMissingUser = testM("should not update user data when user not found") {
    val dto = PatchUserDataReq(nickName = Some(Nickname("Other name")), language = None)

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState()
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.patchUserData(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(UserNotFound(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updatePassword = testM("should update user password") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      _                   <- UsersService.updatePasswordForUser(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(
        hasSameElements(Set(UserAuth(ExampleUserId, "bcrypt(new-secret-password)", confirmed = true, enabled = true)))
      ) &&
      assert(finalUsersRepoState.tokens)(equalTo(initUsersRepoState.tokens)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updatePasswordWithMissingEmail = testM("should not update user password if emails is missing") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser)
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.updatePasswordForUser(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(EmailNotFound(ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updatePasswordWithInvalidCredentials = testM("should not update user password if current password is invalid") {
    val dto = UpdatePasswordReq(ExampleUserEmail, "invalid-password", Password("new-secret-password"))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.updatePasswordForUser(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(InvalidPassword(ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updatePasswordWithInvalidEmail = testM("should not update user password if email address does not match") {
    val dto = UpdatePasswordReq(Email("other@example.org"), ExampleUserPassword, Password("new-secret-password"))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.updatePasswordForUser(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(EmailDigestDoesNotMatch(ExampleUserId, "digest(other@example.org)", ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updatePasswordWithTheSameValue = testM("should not update user password if new password is equal previous one") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password(ExampleUserPassword))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.updatePasswordForUser(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(PasswordsEqual(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updatePasswordForInactiveUser = testM("should not update password is user is inactive") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = false))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.updatePasswordForUser(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(UserIsNotActive(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updatePasswordForNonExistingUser = testM("should nor update password if user not exists") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState()
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.updatePasswordForUser(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(UserNotFound(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val passwordResetRequest = testM("should generate reset password token") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      token               <- UsersService.requestPasswordResetForUser(ExampleUserEmail).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
      expectedToken = TemporaryToken("000102030405060708090A0B0C0D0E0F1011121314151617", ExampleUserId, TokenType.PasswordReset)
    } yield assert(token)(equalTo(expectedToken)) &&
      assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(equalTo(initUsersRepoState.auth)) &&
      assert(finalUsersRepoState.tokens)(hasSameElements(Set(expectedToken))) &&
      assert(sentMessages)(
        equalTo(List(InternalMessage.PasswordResetRequested(ExampleUser, ExampleUserEmail, expectedToken.token, Some(Context.Empty))))
      )
  }

  private val passwordResetRequestForNonExistingEmail = testM("should not generate reset password token if email not exists") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState()
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.requestPasswordResetForUser(ExampleUserEmail).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(EmailNotFound(ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val passwordResetRequestForInactiveUser = testM("should not generate reset password token if user is inactive") {
    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = false))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.requestPasswordResetForUser(ExampleUserEmail).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(UserIsNotActive(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val passwordResetWithToken = testM("should reset password") {
    val dto = PasswordResetReq(Password("new-secret-password"), ExampleUserEmail)

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
                             tokens = Set(
                               TemporaryToken("abc", ExampleUserId, TokenType.PasswordReset),
                               TemporaryToken("xyz", ExampleUserId, TokenType.PasswordReset)
                             )
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      _                   <- UsersService.passwordResetUsingToken("abc", dto).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(
        hasSameElements(Set(UserAuth(ExampleUserId, "bcrypt(new-secret-password)", confirmed = true, enabled = true)))
      ) &&
      assert(finalUsersRepoState.tokens)(isEmpty) &&
      assert(sentMessages)(isEmpty)
  }

  private val passwordResetWithTokenForInactiveUser = testM("should not reset password if user is not active") {
    val dto = PasswordResetReq(Password("new-secret-password"), ExampleUserEmail)

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = false)),
                             tokens = Set(
                               TemporaryToken("abc", ExampleUserId, TokenType.PasswordReset),
                               TemporaryToken("xyz", ExampleUserId, TokenType.PasswordReset)
                             )
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.passwordResetUsingToken("abc", dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(UserIsNotActive(ExampleUserId)))) &&
      assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(equalTo(initUsersRepoState.auth)) &&
      assert(finalUsersRepoState.tokens)(equalTo(initUsersRepoState.tokens)) &&
      assert(sentMessages)(isEmpty)
  }

  private val passwordResetWithTokenForInvalidEmail = testM("should not reset password if email address does not match") {
    val dto = PasswordResetReq(Password("new-secret-password"), Email("other@example.org"))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
                             tokens = Set(
                               TemporaryToken("abc", ExampleUserId, TokenType.PasswordReset),
                               TemporaryToken("xyz", ExampleUserId, TokenType.PasswordReset)
                             )
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.passwordResetUsingToken("abc", dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(EmailDigestDoesNotMatch(ExampleUserId, "digest(other@example.org)", ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(equalTo(initUsersRepoState.auth)) &&
      assert(finalUsersRepoState.tokens)(equalTo(initUsersRepoState.tokens)) &&
      assert(sentMessages)(isEmpty)
  }

  private val createApiKey = testM("should create new API key") {
    val dto = NewPersonalApiKeyReq(ApiKeyDescription("My Key"), None)
    val initUsersRepoState = UsersRepoFake.UsersRepoState()
    val expectedKey =
      ApiKey(
        FirstRandomFuuid,
        "000102030405060708090a0b0c0d0e0f1011121314151617",
        ExampleUserId,
        ApiKeyType.Personal,
        "My Key",
        enabled = true,
        None,
        UsersRepoFake.RepoTimeNow,
        UsersRepoFake.RepoTimeNow
      )

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo           <- Ref.make(initUsersRepoState)
      key                 <- UsersService.createApiKeyForUser(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(key)(equalTo(expectedKey)) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState.copy(apiKeys = initUsersRepoState.apiKeys + expectedKey))) &&
      assert(sentMessages)(isEmpty)
  }

  private val getApiKeys = testM("should get API key") {
    val otherUsersKey = ExampleApiKey.copy(id = ExampleFuuid2, key = "XYZ", userId = ExampleFuuid3)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey, otherUsersKey))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo           <- Ref.make(initUsersRepoState)
      keys                <- UsersService.getApiKeysOf(ExampleUserId, ApiKeyType.Personal).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(keys)(hasSameElements(List(ExampleApiKey))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val deleteApiKey = testM("should delete API key") {
    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo           <- Ref.make(initUsersRepoState)
      _                   <- UsersService.deleteApiKeyAs(ExampleUserId, ExampleApiKeyId).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(finalUsersRepoState)(equalTo(initUsersRepoState.copy(apiKeys = Set.empty))) &&
      assert(sentMessages)(isEmpty)
  }

  private val deleteNonExistingApiKey = testM("should not delete API key if key not found") {
    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey.copy(id = ExampleFuuid3)))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.deleteApiKeyAs(ExampleUserId, ExampleFuuid1).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(ApiKeyNotFound(ExampleFuuid1)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val deleteApiKeyOwnedByAnotherUser = testM("should not delete API key which belongs to another user") {
    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey.copy(userId = ExampleFuuid3)))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <-
        UsersService.deleteApiKeyAs(ExampleUserId, ExampleApiKeyId).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(ApiKeyBelongsToAnotherUser(ExampleApiKeyId, ExampleUserId, ExampleFuuid3)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updateApiKey = testM("should update API key") {
    val dto = UpdateApiKeyDataReq(None, Some(false), Some(OptionalValue(Some(Instant.EPOCH.plusSeconds(60)))))

    val expectedKey = ExampleApiKey.copy(enabled = false, validTo = Some(Instant.EPOCH.plusSeconds(60)))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <- UsersService.updateApiKeyAs(ExampleUserId, ExampleApiKeyId, dto).provideLayer(createUsersService(usersRepo, internalTopic))
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(equalTo(expectedKey)) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState.copy(apiKeys = Set(expectedKey)))) &&
      assert(sentMessages)(isEmpty)
  }

  private val updateApiKeyWithEmptyUpdatesSet = testM("should not update API key if nothing has changed") {
    val dto = UpdateApiKeyDataReq(None, None, None)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <-
        UsersService.updateApiKeyAs(ExampleUserId, ExampleFuuid1, dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(NoUpdates("API key", ExampleFuuid1, dto)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updateNonExistingApiKey = testM("should not update API key if key not found") {
    val dto = UpdateApiKeyDataReq(None, Some(false), Some(OptionalValue(Some(Instant.EPOCH.plusSeconds(60)))))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <-
        UsersService.updateApiKeyAs(ExampleUserId, ExampleFuuid3, dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(ApiKeyNotFound(ExampleFuuid3)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val updateApiKeyOwnedByAnotherUser = testM("should not update API key owned by another user") {
    val dto = UpdateApiKeyDataReq(None, Some(false), Some(OptionalValue(Some(Instant.EPOCH.plusSeconds(60)))))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey.copy(userId = ExampleFuuid3)))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo           <- Ref.make(initUsersRepoState)
      result              <-
        UsersService.updateApiKeyAs(ExampleUserId, ExampleApiKeyId, dto).provideLayer(createUsersService(usersRepo, internalTopic)).either
      finalUsersRepoState <- usersRepo.get
      sentMessages        <- internalTopic.get
    } yield assert(result)(isLeft(equalTo(ApiKeyBelongsToAnotherUser(ExampleApiKeyId, ExampleUserId, ExampleFuuid3)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

}
