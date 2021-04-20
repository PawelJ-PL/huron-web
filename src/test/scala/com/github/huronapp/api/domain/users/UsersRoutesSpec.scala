package com.github.huronapp.api.domain.users

import cats.data.Chain
import com.github.huronapp.api.constants.{Config, MiscConstants, Users}
import com.github.huronapp.api.domain.users.UsersRoutes.UserRoutes
import com.github.huronapp.api.domain.users.dto.{
  GeneratePasswordResetReq,
  LoginReq,
  NewUserReq,
  PasswordResetReq,
  PatchUserDataReq,
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
import org.http4s.{Header, Method, Request, ResponseCookie, Status}
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
      loginUserPasswordNotDefined,
      loginUserInvalidCredentials,
      logoutUser,
      logoutUserUnauthorized,
      userData,
      userDataNotFound,
      userDataUnauthorized,
      updateUserData,
      updateUserDataNoUpdates,
      updateUserDataUserNotFound,
      updateUserDataUserUnauthorized,
      changePassword,
      changePasswordUserNotFound,
      changePasswordInvalidCredentials,
      changePasswordPasswordsEqual,
      changePasswordUnauthorized,
      resetPasswordRequest,
      resetPasswordRequestEmailNotFound,
      resetPasswordRequestInactiveUser,
      resetPasswordWithToken,
      resetPasswordWithTokenInvalid,
      resetPasswordWithTokenInactiveUser
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
      result      <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      body        <- result.as[ErrorResponse.BadRequest]
    } yield assert(result.status)(equalTo(Status.BadRequest)) &&
      assert(body)(equalTo(ErrorResponse.BadRequest("Invalid request")))
  }

  private val registerUser = testM("should generate response on registration success") {
    val dto = NewUserReq(ExampleUserNickName, ExampleUserEmail, ExampleUserPassword, Some(ExampleUserLanguage))

    val responses = UsersServiceStub.UsersServiceResponses()

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      loggedMessages   <- logs.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Created)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(SessionRepoFake.SessionRepoState()))
  }

  private val registerUserWithConflict = testM("should generate response on registration conflict") {
    val dto = NewUserReq(ExampleUserNickName, ExampleUserEmail, ExampleUserPassword, Some(ExampleUserLanguage))

    val responses = UsersServiceStub.UsersServiceResponses(createUser = ZIO.fail(EmailAlreadyRegistered(ExampleUserEmailDigest)))

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
        result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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

  private val loginUserPasswordNotDefined = testM("should generate response for failed login if password was not set") {
    val dto = LoginReq(ExampleUserEmail, ExampleUserPassword)

    val responses = UsersServiceStub.UsersServiceResponses(login = ZIO.fail(PasswordNotDefined(ExampleUserId)))

    for {
      sessionRepo      <- Ref.make(SessionRepoFake.SessionRepoState())
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/auth/login").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      body             <- result.as[ErrorResponse.Unauthorized]
      loggedMessages   <- logs.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(body)(equalTo(ErrorResponse.Unauthorized("Invalid credentials"))) &&
      assert(result.headers.get(CaseInsensitiveString("set-cookie")))(isNone) &&
      assert(result.headers.get(CaseInsensitiveString("x-csrf-token")))(isNone) &&
      assert(loggedMessages)(
        equalTo(Chain.one("Credentials verification failed: Password not defined for user 431e092f-50ce-47eb-afbd-b806514d3f2c"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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

  private val logoutUserUnauthorized = testM("should generate response for logout if user unauthorized") {
    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState(sessions = Set(exampleSession))

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.DELETE, uri = uri"/api/v1/users/me/session")
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      body             <- result.as[UserDataResp]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Ok)) &&
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val updateUserData = testM("should generate response for user data update") {
    val dto = PatchUserDataReq(nickName = Some("New Name"), language = None)

    val responses = UsersServiceStub.UsersServiceResponses(patchUser = ZIO.succeed(ExampleUser.copy(nickName = "New Name")))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PATCH, uri = uri"/api/v1/users/me/data").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      body             <- result.as[UserDataResp]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Ok)) &&
      assert(body)(equalTo(UserDataResp(ExampleUserId, "New Name", ExampleUserLanguage))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val updateUserDataNoUpdates = testM("should generate response for user data update if no updates provided") {
    val dto = PatchUserDataReq(nickName = None, language = None)

    val responses = UsersServiceStub.UsersServiceResponses(patchUser = ZIO.fail(NoUpdates(ExampleUserId, dto)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PATCH, uri = uri"/api/v1/users/me/data").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.Unauthorized)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val changePassword = testM("should generate response for password change") {
    val dto = UpdatePasswordReq(ExampleUserPassword, "new-password")

    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/password").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.NoContent)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val changePasswordUserNotFound = testM("should generate response for password change if user not found") {
    val dto = UpdatePasswordReq(ExampleUserPassword, "new-password")

    val responses = UsersServiceStub.UsersServiceResponses(updatePassword = ZIO.fail(UserNotFound(ExampleUserId)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/password").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val changePasswordInvalidCredentials = testM("should generate response for password change if old password is incorrect") {
    val dto = UpdatePasswordReq(ExampleUserPassword, "new-password")

    val responses = UsersServiceStub.UsersServiceResponses(updatePassword = ZIO.fail(InvalidPassword(ExampleUserEmailDigest)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/password").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      body             <- result.as[ErrorResponse.PreconditionFailed]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(equalTo(ErrorResponse.PreconditionFailed("Current password is incorrect", Some("InvalidCurrentPassword")))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val changePasswordPasswordsEqual = testM("should generate response for password change if old and new passwords are equal") {
    val dto = UpdatePasswordReq(ExampleUserPassword, "new-password")

    val responses = UsersServiceStub.UsersServiceResponses(updatePassword = ZIO.fail(PasswordsEqual(ExampleUserId)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/password").withHeaders(validAuthHeader).withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      body             <- result.as[ErrorResponse.PreconditionFailed]
      finalSessionRepo <- sessionRepo.get
    } yield assert(result.status)(equalTo(Status.PreconditionFailed)) &&
      assert(body)(equalTo(ErrorResponse.PreconditionFailed("New and old passwords are equal", Some("PasswordsEqual")))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val changePasswordUnauthorized = testM("should generate response for password change if user unathorized") {
    val dto = UpdatePasswordReq(ExampleUserPassword, "new-password")

    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.POST, uri = uri"/api/v1/users/me/password").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
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
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NoContent)) &&
      assert(loggedMessages)(equalTo(Chain.one("Unable to reset password: User 431e092f-50ce-47eb-afbd-b806514d3f2c is not active"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val resetPasswordWithToken = testM("should generate response for password reset") {
    val dto = PasswordResetReq("new-password")

    val responses = UsersServiceStub.UsersServiceResponses()

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PUT, uri = uri"/api/v1/users/password/abc").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NoContent)) &&
      assert(loggedMessages)(equalTo(Chain.empty)) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val resetPasswordWithTokenInvalid = testM("should generate response for password reset if token is invalid") {
    val dto = PasswordResetReq("new-password")

    val responses = UsersServiceStub.UsersServiceResponses(passwordReset = ZIO.fail(NoValidTokenFound("abc")))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PUT, uri = uri"/api/v1/users/password/abc").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(loggedMessages)(equalTo(Chain.one("Password reset failed: Token abc is not valid"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

  private val resetPasswordWithTokenInactiveUser = testM("should generate response for password reset if user is inactive") {
    val dto = PasswordResetReq("new-password")

    val responses = UsersServiceStub.UsersServiceResponses(passwordReset = ZIO.fail(UserIsNotActive(ExampleUserId)))

    val initSessionRepo = SessionRepoFake.SessionRepoState()

    for {
      sessionRepo      <- Ref.make(initSessionRepo)
      logs             <- Ref.make(Chain.empty[String])
      routes           <- UsersRoutes.routes.provideLayer(routesLayer(sessionRepo, responses, logs))
      req = Request[RouteEffect](method = Method.PUT, uri = uri"/api/v1/users/password/abc").withEntity(dto)
      result           <- routes.run(req).value.someOrFail(new RuntimeException("Missing response"))
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logs.get
    } yield assert(result.status)(equalTo(Status.NotFound)) &&
      assert(loggedMessages)(equalTo(Chain.one("Password reset failed: User 431e092f-50ce-47eb-afbd-b806514d3f2c is not active"))) &&
      assert(finalSessionRepo)(equalTo(initSessionRepo))
  }

}
