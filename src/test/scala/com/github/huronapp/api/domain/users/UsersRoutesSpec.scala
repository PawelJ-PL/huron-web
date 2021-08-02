package com.github.huronapp.api.domain.users

import cats.data.Chain
import com.github.huronapp.api.constants.{Config, MiscConstants, Users}
import com.github.huronapp.api.domain.users.UsersRoutes.UserRoutes
import com.github.huronapp.api.domain.users.dto.fields.{ApiKeyDescription, Nickname, Password, PrivateKey, PublicKey, KeyPair => KeyPairDto}
import com.github.huronapp.api.domain.users.dto.{
  ApiKeyDataResp,
  GeneratePasswordResetReq,
  LoginReq,
  NewPersonalApiKeyReq,
  NewUserReq,
  PasswordResetReq,
  PatchUserDataReq,
  UpdateApiKeyDataReq,
  UpdatePasswordReq,
  UserDataResp
}
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import com.github.huronapp.api.http.ErrorResponse
import com.github.huronapp.api.testdoubles.HttpAuthenticationFake.validAuthHeader
import com.github.huronapp.api.testdoubles.{HttpAuthenticationFake, LoggerFake, RandomUtilsStub, SessionRepoFake, UsersServiceStub}
import io.circe.Json
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.implicits._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Header, Headers, Method, Request, ResponseCookie, Status, Uri}
import zio.clock.Clock
import zio.{Ref, ZIO, ZLayer}
import zio.interop.catz._
import zio.test.environment.TestEnvironment
import zio.test.Assertion.{equalTo, isEmpty, isNone, isSome}
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

import java.time.Instant

object UsersRoutesSpec extends DefaultRunnableSpec with Config with Users with MiscConstants {

  private def routesLayer(
    sessionRepo: Ref[SessionRepoFake.SessionRepoState],
    usersServiceResponses: UsersServiceStub.UsersServiceResponses,
    logs: Ref[Chain[String]]
  ): ZLayer[TestEnvironment, Nothing, UserRoutes] =
    UsersServiceStub.withResponses(usersServiceResponses) ++ LoggerFake.usingRef(logs) ++ ZLayer.succeed(
      ExampleSecurityConfig
    ) ++ (RandomUtilsStub.create ++ Clock.any >>> SessionRepoFake.create(sessionRepo)) ++ HttpAuthenticationFake.create >>> UsersRoutes.live

  private def apiKeyToResponse(apiKey: ApiKey) =
    ApiKeyDataResp(apiKey.id, apiKey.key, apiKey.enabled, apiKey.description, apiKey.validTo, apiKey.createdAt, apiKey.updatedAt)

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Users routes suite")(
      decodingFailureHandling,
      registerUser,
      registerUserWithConflict,
      confirmRegistration,
      confirmRegistrationWithInvalidToken,
      confirmRegistrationWithAlreadyConfirmed,
      loginUser,
      loginUserMissingEmail,
      loginUserInactiveUser,
      loginUserInvalidCredentials,
      logoutUser,
      logoutApiKeyUser,
      logoutUserUnauthorized,
      userData,
      userDataAuthenticatedWithApiKey,
      userDataNotFound,
      userDataUnauthorized,
      updateUserData,
      updateUserDataNoUpdates,
      updateUserDataUserNotFound,
      updateUserDataUserUnauthorized,
      changePassword,
      changePasswordUserNotFound,
      changePasswordInvalidCredentials,
      changePasswordInvalidEmails,
      changePasswordPasswordsEqual,
      changePasswordUnauthorized,
      resetPasswordRequest,
      resetPasswordRequestEmailNotFound,
      resetPasswordRequestInactiveUser,
      resetPasswordWithToken,
      resetPasswordWithTokenInvalid,
      resetPasswordWithTokenInactiveUser,
      resetPasswordWithInvalidEmail,
      createApiKey,
      createApiKeyUnauthorized,
      listApiKeys,
      listApiKeysUnauthorized,
      deleteApiKey,
      deleteNonExistingApiKey,
      deleteApiKeyOwnedByAnotherUser,
      deleteApiKeyUnauthorized,
      updateApiKey,
      updateApiKeyWithNoUpdates,
      updateNonExistingApiKey,
      updateApiKeyOwnedByAnotherUser,
      updateApiKeyUnauthorized,
      getKeyPairs,
      getNonExistingKeyPairs,
      getKeyPairsUnauthorized
    )

  private val exampleSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH)

  private val decodingFailureHandling = testM("should generate proper response on decoding failure") {
    val dto = Json.obj("foo" -> Json.fromString("bar"))

    val responses = UsersServiceStub.UsersServiceResponses()

    for {
      sessionRepo <- Ref.make(SessionRepoFake.SessionRepoState())
      logs        <- Ref.make(Chain.empty[String])
      routes      <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users").withEntity(dto)
      result      <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body        <- result.as[ErrorResponse.BadRequest]
    } yield assert(result.status)(equalTo(Status.BadRequest)) &&
      assert(body)(equalTo(ErrorResponse.BadRequest("Invalid request")))
  }

  private val keyPairDto = KeyPairDto(KeyAlgorithm.Rsa, PublicKey(ExamplePublicKey), PrivateKey(ExamplePrivateKey))

  private val registerUser = testM("should generate response on registration success") {
    val dto =
      NewUserReq(Nickname(ExampleUserNickName), ExampleUserEmail, Password(ExampleUserPassword), Some(ExampleUserLanguage), keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses()

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      loggedMessages   <- logs.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Created)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(SessionRepoFake.SessionRepoState()))
  }

  private val registerUserWithConflict = testM("should generate response on registration conflict") {
    val dto =
      NewUserReq(Nickname(ExampleUserNickName), ExampleUserEmail, Password(ExampleUserPassword), Some(ExampleUserLanguage), keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses(createUser = ZIO.fail(EmailAlreadyRegistered(ExampleUserEmailDigest)))

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.Conflict]
      loggedMessages   <- logs.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Conflict)) &&
      assert(body)(equalTo(ErrorResponse.Conflict("Email already registered"))) &&
      assert(loggedMessages)(
        equalTo(Chain.one("Unable to register user: Email with digest digest(alice@example.org) already registered"))
      ) &&
      assert(finalSessionRepo)(equalTo(SessionRepoFake.SessionRepoState()))
  }

  private val confirmRegistration = testM("should generate response on signup confirmation success") {
    val responses = UsersServiceStub.UsersServiceResponses()

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/registrations/abc")
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      loggedMessages   <- logs.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.NoContent)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(SessionRepoFake.SessionRepoState()))
  }

  private val confirmRegistrationWithInvalidToken = testM("should generate response on signup confirmation failure with invalid token") {
    val responses = UsersServiceStub.UsersServiceResponses(confirmRegistration = ZIO.fail(NoValidTokenFound("abc")))

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/registrations/abc")
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.NotFound]
      loggedMessages   <- logs.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound("Invalid token"))) &&
      assert(loggedMessages)(equalTo(Chain.one("SignUp confirmation failed: Token abc is not valid"))) &&
      assert(finalSessionRepo)(equalTo(SessionRepoFake.SessionRepoState()))
  }

  private val confirmRegistrationWithAlreadyConfirmed =
    testM("should generate response on signup confirmation failure with already confirmed user") {
      val responses = UsersServiceStub.UsersServiceResponses(confirmRegistration = ZIO.fail(RegistrationAlreadyConfirmed(ExampleUserId)))

      for {
        sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
        logs             <- Ref.make(Chain.empty[String])
        routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
        req = Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/registrations/abc")
        result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
        body             <- result.as[ErrorResponse.NotFound]
        loggedMessages   <- logs.get
        finalSessionRepo <- sessionRepo.get
      } yield assert(result.status)(equalTo(Status.NotFound)) &&
        assert(body)(equalTo(ErrorResponse.NotFound("Invalid token"))) &&
        assert(loggedMessages)(equalTo(Chain.one("SignUp confirmation failed: Registration for user was already confirmed"))) &&
        assert(finalSessionRepo)(equalTo(SessionRepoFake.SessionRepoState()))
    }

  private val loginUser = testM("should generate response successful login") {
    val dto = LoginReq(ExampleUserEmail, ExampleUserPassword)

    val responses = UsersServiceStub.UsersServiceResponses()

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/auth/login").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[UserDataResp]
      loggedMessages   <- logs.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(equalTo(UserDataResp(ExampleUserId, ExampleUserNickName, ExampleUserLanguage))) &&
      assert(result.headers.get(CaseInsensitiveString("set-cookie")))(
        isSome(
          equalTo(
            Header(
              "Set-Cookie",
              ResponseCookie("session", FirstRandomFuuid.show, maxAge = Some(604800), path = Some("/"), httpOnly = true).renderString
            )
          )
        )
      ) &&
      assert(result.headers.get(CaseInsensitiveString("x-csrf-token")))(isSome(equalTo(Header("X-Csrf-Token", SecondRandomFuuid.show)))) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(
        equalTo(
          SessionRepoFake.SessionRepoState(sessions = Set(UserSession(FirstRandomFuuid, ExampleUserId, SecondRandomFuuid, Instant.EPOCH)))
        )
      )
  }

  private val loginUserMissingEmail = testM("should generate response for failed login if email not found") {
    val dto = LoginReq(ExampleUserEmail, ExampleUserPassword)

    val responses = UsersServiceStub.UsersServiceResponses(login = ZIO.fail(EmailNotFound(ExampleUserEmailDigest)))

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/auth/login").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.Unauthorized]
      loggedMessages   <- logs.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(body)(equalTo(ErrorResponse.Unauthorized("Invalid credentials"))) &&
      assert(result.headers.get(CaseInsensitiveString("set-cookie")))(isNone) &&
      assert(result.headers.get(CaseInsensitiveString("x-csrf-token")))(isNone) &&
      assert(loggedMessages)(
        equalTo(Chain.one("Credentials verification failed: User with email hash digest(alice@example.org) not found"))
      ) &&
      assert(finalSessionRepo)(equalTo(SessionRepoFake.SessionRepoState()))
  }

  private val loginUserInactiveUser = testM("should generate response for failed login if user is inactive") {
    val dto = LoginReq(ExampleUserEmail, ExampleUserPassword)

    val responses = UsersServiceStub.UsersServiceResponses(login = ZIO.fail(UserIsNotActive(ExampleUserId)))

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/auth/login").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.Unauthorized]
      loggedMessages   <- logs.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(body)(equalTo(ErrorResponse.Unauthorized("Invalid credentials"))) &&
      assert(result.headers.get(CaseInsensitiveString("set-cookie")))(isNone) &&
      assert(result.headers.get(CaseInsensitiveString("x-csrf-token")))(isNone) &&
      assert(loggedMessages)(
        equalTo(Chain.one("Credentials verification failed: User 431e092f-50ce-47eb-afbd-b806514d3f2c is not active"))
      ) &&
      assert(finalSessionRepo)(equalTo(SessionRepoFake.SessionRepoState()))
  }

  private val loginUserInvalidCredentials = testM("should generate response for failed login if credentials are invalid") {
    val dto = LoginReq(ExampleUserEmail, ExampleUserPassword)

    val responses = UsersServiceStub.UsersServiceResponses(login = ZIO.fail(InvalidPassword(ExampleUserEmailDigest)))

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/auth/login").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.Unauthorized]
      loggedMessages   <- logs.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(body)(equalTo(ErrorResponse.Unauthorized("Invalid credentials"))) &&
      assert(result.headers.get(CaseInsensitiveString("set-cookie")))(isNone) &&
      assert(result.headers.get(CaseInsensitiveString("x-csrf-token")))(isNone) &&
      assert(loggedMessages)(
        equalTo(Chain.one("Credentials verification failed: Invalid password for user with email hash digest(alice@example.org)"))
      ) &&
      assert(finalSessionRepo)(equalTo(SessionRepoFake.SessionRepoState()))
  }

  private val logoutUser = testM("should generate response successful logout") {
    val responses = UsersServiceStub.UsersServiceResponses()

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState(sessions = Set(exampleSession)))
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.DELETE, uri = uri"/api/v1/users/me/session").withHeaders(validAuthHeader)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.NoContent)) &&
      assert(result.headers.get(CaseInsensitiveString("set-cookie")))(
        isSome(
          equalTo(
            Header("Set-Cookie", ResponseCookie("session", "invalid", maxAge = Some(0), path = Some("/"), httpOnly = true).renderString)
          )
        )
      ) &&
      assert(finalSessionRepo.sessions)(isEmpty)
  }

  private val logoutApiKeyUser = testM("should generate logout response for user authenticated with API key") {
    val responses = UsersServiceStub.UsersServiceResponses()
    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set(exampleSession))

    for {
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.DELETE, uri = uri"/api/v1/users/me/session")
              .withHeaders(Headers.of(Header("X-Api-Key", "UserOk")))
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.Forbidden]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Forbidden)) &&
      assert(body)(equalTo(ErrorResponse.Forbidden("Logout not allowed for this authentication method"))) &&
      assert(result.headers.get(CaseInsensitiveString("set-cookie")))(isNone) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState))
  }

  private val logoutUserUnauthorized = testM("should generate response for logout if user unauthorized") {
    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState(sessions = Set(exampleSession))

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.DELETE, uri = uri"/api/v1/users/me/session")
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(result.headers.get(CaseInsensitiveString("set-cookie")))(isNone) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val userData = testM("should generate response for user data") {
    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/me/data").withHeaders(validAuthHeader)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[UserDataResp]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(result.headers.get(CaseInsensitiveString("X-Csrf-Token")))(isSome(equalTo(Header("X-Csrf-Token", ExampleFuuid2.show)))) &&
      assert(body)(equalTo(UserDataResp(ExampleUserId, ExampleUserNickName, ExampleUserLanguage))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val userDataAuthenticatedWithApiKey = testM("should generate response for API key user data") {
    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req =
        Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/me/data").withHeaders(Headers.of(Header("X-Api-Key", "UserOk")))
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[UserDataResp]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(result.headers.get(CaseInsensitiveString("X-Csrf-Token")))(isNone) &&
      assert(body)(equalTo(UserDataResp(ExampleUserId, ExampleUserNickName, ExampleUserLanguage))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val userDataNotFound = testM("should generate response for user data if not found") {
    val responses = UsersServiceStub.UsersServiceResponses(userData = ZIO.none)

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/me/data").withHeaders(validAuthHeader)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val userDataUnauthorized = testM("should generate response for user data if unauthorized") {
    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/me/data")
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val updateUserData = testM("should generate response for user data update") {
    val dto = PatchUserDataReq(nickName = Some(Nickname("NewName")), language = None)

    val responses = UsersServiceStub.UsersServiceResponses(patchUser = ZIO.succeed(ExampleUser.copy(nickName = "NewName")))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PATCH, uri = uri"/api/v1/users/me/data").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[UserDataResp]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(equalTo(UserDataResp(ExampleUserId, "NewName", ExampleUserLanguage))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val updateUserDataNoUpdates = testM("should generate response for user data update if no updates provided") {
    val dto = PatchUserDataReq(nickName = None, language = None)

    val responses = UsersServiceStub.UsersServiceResponses(patchUser = ZIO.fail(NoUpdates("user", ExampleUserId, dto)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PATCH, uri = uri"/api/v1/users/me/data").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.BadRequest]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.BadRequest)) &&
      assert(body)(equalTo(ErrorResponse.BadRequest("No updates in request"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val updateUserDataUserNotFound = testM("should generate response for user data update if user not found") {
    val dto = PatchUserDataReq(nickName = None, language = None)

    val responses = UsersServiceStub.UsersServiceResponses(patchUser = ZIO.fail(UserNotFound(ExampleUserId)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PATCH, uri = uri"/api/v1/users/me/data").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.NotFound]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound("User not found"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val updateUserDataUserUnauthorized = testM("should generate response for user data update if user not authorized") {
    val dto = PatchUserDataReq(nickName = None, language = None)

    val responses = UsersServiceStub.UsersServiceResponses(patchUser = ZIO.fail(UserNotFound(ExampleUserId)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PATCH, uri = uri"/api/v1/users/me/data").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val changePassword = testM("should generate response for password change") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"), keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/password").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.NoContent)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val changePasswordUserNotFound = testM("should generate response for password change if user not found") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"), keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses(updatePassword = ZIO.fail(UserNotFound(ExampleUserId)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/password").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val changePasswordInvalidCredentials = testM("should generate response for password change if old password is incorrect") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"), keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses(updatePassword = ZIO.fail(InvalidPassword(ExampleUserEmailDigest)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/password").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.PreconditionFailed]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(equalTo(ErrorResponse.PreconditionFailed("Current password is incorrect", Some("InvalidCurrentPassword")))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val changePasswordInvalidEmails = testM("should generate response for password change if email address is incorrect") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"), keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses(updatePassword =
      ZIO.fail(EmailDigestDoesNotMatch(ExampleUserId, ExampleUserEmailDigest, "digest(other)"))
    )

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/password").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.PreconditionFailed]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(equalTo(ErrorResponse.PreconditionFailed("Email is incorrect", Some("InvalidEmail")))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val changePasswordPasswordsEqual = testM("should generate response for password change if old and new passwords are equal") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"), keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses(updatePassword = ZIO.fail(PasswordsEqual(ExampleUserId)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/password").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.PreconditionFailed]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(equalTo(ErrorResponse.PreconditionFailed("New and old passwords are equal", Some("PasswordsEqual")))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val changePasswordUnauthorized = testM("should generate response for password change if user unathorized") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"), keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/password").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val resetPasswordRequest = testM("should generate response for password reset request") {
    val dto = GeneratePasswordResetReq(ExampleUserEmail)

    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/password").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NoContent)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val resetPasswordRequestEmailNotFound = testM("should generate response for password reset request if email not found") {
    val dto = GeneratePasswordResetReq(ExampleUserEmail)

    val responses = UsersServiceStub.UsersServiceResponses(passwordResetRequest = ZIO.fail(EmailNotFound(ExampleUserEmailDigest)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/password").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NoContent)) &&
      assert(loggedMessages)(equalTo(Chain.one("Unable to reset password: User with email hash digest(alice@example.org) not found"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val resetPasswordRequestInactiveUser = testM("should generate response for password reset request if user is inactive") {
    val dto = GeneratePasswordResetReq(ExampleUserEmail)

    val responses = UsersServiceStub.UsersServiceResponses(passwordResetRequest = ZIO.fail(UserIsNotActive(ExampleUserId)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/password").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NoContent)) &&
      assert(loggedMessages)(equalTo(Chain.one("Unable to reset password: User 431e092f-50ce-47eb-afbd-b806514d3f2c is not active"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val resetPasswordWithToken = testM("should generate response for password reset") {
    val dto = PasswordResetReq(Password("new-secret-password"), ExampleUserEmail, keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PUT, uri = uri"/api/v1/users/password/abc").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NoContent)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val resetPasswordWithTokenInvalid = testM("should generate response for password reset if token is invalid") {
    val dto = PasswordResetReq(Password("new-secret-password"), ExampleUserEmail, keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses(passwordReset = ZIO.fail(NoValidTokenFound("abc")))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PUT, uri = uri"/api/v1/users/password/abc").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(loggedMessages)(equalTo(Chain.one("Password reset failed: Token abc is not valid"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val resetPasswordWithTokenInactiveUser = testM("should generate response for password reset if user is inactive") {
    val dto = PasswordResetReq(Password("new-secret-password"), ExampleUserEmail, keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses(passwordReset = ZIO.fail(UserIsNotActive(ExampleUserId)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PUT, uri = uri"/api/v1/users/password/abc").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(loggedMessages)(equalTo(Chain.one("Password reset failed: User 431e092f-50ce-47eb-afbd-b806514d3f2c is not active"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val resetPasswordWithInvalidEmail = testM("should generate response for password reset if email is incorrect") {
    val dto = PasswordResetReq(Password("new-secret-password"), ExampleUserEmail, keyPairDto)

    val responses = UsersServiceStub.UsersServiceResponses(passwordReset =
      ZIO.fail(EmailDigestDoesNotMatch(ExampleUserId, ExampleUserEmailDigest, "otherDigest"))
    )

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PUT, uri = uri"/api/v1/users/password/abc").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(loggedMessages)(
        equalTo(
          Chain.one(
            "Password reset failed: The digest of user 431e092f-50ce-47eb-afbd-b806514d3f2c email address was expected to be digest(alice@example.org), but otherDigest was found"
          )
        )
      ) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val createApiKey = testM("should generate response for new API key") {
    val dto = NewPersonalApiKeyReq(ApiKeyDescription("My Key"), None)

    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/api-keys").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ApiKeyDataResp]
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(equalTo(apiKeyToResponse(ExampleApiKey))) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val createApiKeyUnauthorized = testM("should generate response for new API key for unauthorized user") {
    val dto = NewPersonalApiKeyReq(ApiKeyDescription("My Key"), None)

    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/api-keys").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val listApiKeys = testM("should generate response for API keys list") {
    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/me/api-keys").withHeaders(validAuthHeader)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[List[ApiKeyDataResp]]
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(equalTo(List(apiKeyToResponse(ExampleApiKey)))) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val listApiKeysUnauthorized = testM("should generate response for API keys list for unauthorized user") {
    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/me/api-keys")
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val deleteApiKey = testM("should generate response for API key delete") {
    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.DELETE, uri = Uri.unsafeFromString(s"/api/v1/users/me/api-keys/$ExampleApiKeyId"))
              .withHeaders(validAuthHeader)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NoContent)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val deleteNonExistingApiKey = testM("should generate response for API key delete if key not found") {
    val responses = UsersServiceStub.UsersServiceResponses(deleteApiKey = ZIO.fail(ApiKeyNotFound(ExampleApiKeyId)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.DELETE, uri = Uri.unsafeFromString(s"/api/v1/users/me/api-keys/$ExampleApiKeyId"))
              .withHeaders(validAuthHeader)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.NotFound]
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound(s"API key $ExampleApiKeyId not found"))) &&
      assert(loggedMessages)(equalTo(Chain.one(s"Unable to delete API key: API key with id $ExampleApiKeyId not found"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val deleteApiKeyOwnedByAnotherUser = testM("should generate response for API key delete if key is owned by another user") {
    val responses = UsersServiceStub.UsersServiceResponses(deleteApiKey =
      ZIO.fail(ApiKeyBelongsToAnotherUser(ExampleApiKeyId, ExampleUserId, ExampleFuuid1))
    )

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.DELETE, uri = Uri.unsafeFromString(s"/api/v1/users/me/api-keys/$ExampleApiKeyId"))
              .withHeaders(validAuthHeader)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.NotFound]
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound(s"API key $ExampleApiKeyId not found"))) &&
      assert(loggedMessages)(
        equalTo(Chain.one(s"Unable to delete API key: API key with ID $ExampleApiKeyId belongs to user $ExampleFuuid1, not $ExampleUserId"))
      ) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val deleteApiKeyUnauthorized = testM("should generate response for API key delete for unauthorized user") {
    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.DELETE, uri = Uri.unsafeFromString(s"/api/v1/users/me/api-keys/$ExampleApiKeyId"))
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val updateApiKey = testM("should generate response for API key update") {
    val dto = UpdateApiKeyDataReq(None, Some(true), None)

    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PATCH, uri = Uri.unsafeFromString(s"/api/v1/users/me/api-keys/$ExampleApiKeyId"))
              .withHeaders(validAuthHeader)
              .withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ApiKeyDataResp]
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(equalTo(apiKeyToResponse(ExampleApiKey))) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val updateApiKeyWithNoUpdates = testM("should generate response for API key update with missing updates") {
    val dto = UpdateApiKeyDataReq(None, None, None)

    val responses = UsersServiceStub.UsersServiceResponses(updateApiKey = ZIO.fail(NoUpdates("API key", ExampleApiKeyId, dto)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PATCH, uri = Uri.unsafeFromString(s"/api/v1/users/me/api-keys/$ExampleApiKeyId"))
              .withHeaders(validAuthHeader)
              .withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.BadRequest]
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.BadRequest)) &&
      assert(body)(equalTo(ErrorResponse.BadRequest("No updates in request"))) &&
      assert(loggedMessages)(equalTo(Chain.one(s"Unable to update API key: No updates provided for API key $ExampleApiKeyId"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val updateNonExistingApiKey = testM("should generate response for API key update if key not found") {
    val dto = UpdateApiKeyDataReq(None, None, None)

    val responses = UsersServiceStub.UsersServiceResponses(updateApiKey = ZIO.fail(ApiKeyNotFound(ExampleApiKeyId)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PATCH, uri = Uri.unsafeFromString(s"/api/v1/users/me/api-keys/$ExampleApiKeyId"))
              .withHeaders(validAuthHeader)
              .withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.NotFound]
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound(s"API key $ExampleApiKeyId not found"))) &&
      assert(loggedMessages)(equalTo(Chain.one(s"Unable to update API key: API key with id $ExampleApiKeyId not found"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val updateApiKeyOwnedByAnotherUser = testM("should generate response for API key update if key owned by another user") {
    val dto = UpdateApiKeyDataReq(None, None, None)

    val responses = UsersServiceStub.UsersServiceResponses(updateApiKey =
      ZIO.fail(ApiKeyBelongsToAnotherUser(ExampleApiKeyId, ExampleUserId, ExampleFuuid1))
    )

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PATCH, uri = Uri.unsafeFromString(s"/api/v1/users/me/api-keys/$ExampleApiKeyId"))
              .withHeaders(validAuthHeader)
              .withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[ErrorResponse.NotFound]
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(body)(equalTo(ErrorResponse.NotFound(s"API key $ExampleApiKeyId not found"))) &&
      assert(loggedMessages)(
        equalTo(Chain.one(s"Unable to update API key: API key with ID $ExampleApiKeyId belongs to user $ExampleFuuid1, not $ExampleUserId"))
      ) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val updateApiKeyUnauthorized = testM("should generate response for API key update if user unauthorized") {
    val dto = UpdateApiKeyDataReq(None, Some(true), None)

    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PATCH, uri = Uri.unsafeFromString(s"/api/v1/users/me/api-keys/$ExampleApiKeyId"))
              .withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val getKeyPairs = testM("should generate response with current users keypair") {
    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()
    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/me/keypair").withHeaders(validAuthHeader)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      body             <- result.as[KeyPairDto]
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(equalTo(KeyPairDto(KeyAlgorithm.Rsa, PublicKey(ExamplePublicKey), PrivateKey(ExamplePrivateKey)))) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val getNonExistingKeyPairs = testM("should generate response if keypair not found for user") {
    val responses = UsersServiceStub.UsersServiceResponses(keyPair = ZIO.none)

    val initSessionRepo = SessionRepoFake.SessionRepoState()
    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/me/keypair").withHeaders(validAuthHeader)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val getKeyPairsUnauthorized = testM("should generate response for current users keypair when unauthorized") {
    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()
    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.GET, uri = uri"/api/v1/users/me/keypair")
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing route"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

}
