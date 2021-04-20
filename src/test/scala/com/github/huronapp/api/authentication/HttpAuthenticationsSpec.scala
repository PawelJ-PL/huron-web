package com.github.huronapp.api.authentication

import cats.data.Chain
import com.github.huronapp.api.authentication.AuthenticatedUser.SessionAuthenticatedUser
import com.github.huronapp.api.authentication.HttpAuthentication.HttpAuthentication
import com.github.huronapp.api.constants.{Config, MiscConstants, Users}
import com.github.huronapp.api.domain.users.{UserAuth, UserSession}
import com.github.huronapp.api.http.ErrorResponse.Unauthorized
import com.github.huronapp.api.testdoubles.{LoggerFake, RandomUtilsStub, SessionRepoFake, UsersRepoFake}
import io.github.gaelrenoux.tranzactio.doobie.Database
import sttp.model.Method
import zio.clock.Clock
import zio.{Ref, ZLayer}
import zio.test.Assertion.{equalTo, isLeft}
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

import java.time.Instant

object HttpAuthenticationsSpec extends DefaultRunnableSpec with Config with MiscConstants with Users {

  private def authentication(
    usersRepo: Ref[UsersRepoFake.UsersRepoState],
    sessionRepo: Ref[SessionRepoFake.SessionRepoState],
    logEntries: Ref[Chain[String]]
  ): ZLayer[TestEnvironment, Nothing, HttpAuthentication] =
    (RandomUtilsStub.create ++ Clock.any >>> SessionRepoFake.create(sessionRepo)) ++ UsersRepoFake.create(
      usersRepo
    ) ++ Database.none ++ LoggerFake.usingRef(logEntries) ++ ZLayer
      .succeed(
        ExampleSecurityConfig
      ) ++ Clock.any >>> HttpAuthentication.live

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Http authentication suite")(
      sessionAuthSuccess,
      sessionAuthFailedNoCookie,
      sessionAuthFailedInvalidSessionId,
      sessionAuthFailedNoSession,
      sessionAuthFailedUserNotExists,
      sessionAuthFailedNotActiveUser,
      sessionAuthFailedMissingCsrfToken,
      sessionAuthFailedInvalidCsrfToken,
      sessionAuthSuccessInvalidCsrfTokenSafeMethod,
      sessionAuthFailedExpired
    )

  private val sessionAuthSuccess = testM("Should authenticate user based on cookies") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid2.show), Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, Some(ExampleUserPasswordHash), confirmed = true, enabled = true))
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set(userSession))

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries))
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result)(equalTo(SessionAuthenticatedUser(userSession))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState))
  }

  private val sessionAuthFailedNoCookie = testM("Session authentication should fail if no session cookie set") {
    val inputs = AuthenticationInputs(None, Some(ExampleFuuid2.show), Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, Some(ExampleUserPasswordHash), confirmed = true, enabled = true))
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set(userSession))

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries)).either
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logEntries.get
    } yield assert(result)(isLeft(equalTo(Unauthorized("Invalid credentials")))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState)) &&
      assert(loggedMessages)(equalTo(Chain.one("Session authentication failed with: SessionCookieNotFound")))
  }

  private val sessionAuthFailedInvalidSessionId = testM("Session authentication should fail if session ID is not valid uuid") {
    val inputs = AuthenticationInputs(Some("Foo"), Some(ExampleFuuid2.show), Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, Some(ExampleUserPasswordHash), confirmed = true, enabled = true))
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set(userSession))

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries)).either
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logEntries.get
    } yield assert(result)(isLeft(equalTo(Unauthorized("Invalid credentials")))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState)) &&
      assert(loggedMessages)(equalTo(Chain.one("Session authentication failed with: NotValidSessionId(Foo,Invalid UUID string: Foo)")))
  }

  private val sessionAuthFailedNoSession = testM("Session authentication should fail if session not found") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid2.show), Method.POST)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, Some(ExampleUserPasswordHash), confirmed = true, enabled = true))
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState()

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries)).either
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logEntries.get
    } yield assert(result)(isLeft(equalTo(Unauthorized("Invalid credentials")))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState)) &&
      assert(loggedMessages)(
        equalTo(Chain.one("Session authentication failed with: SessionNotExists(e85630b6-fb45-4a1b-a963-5251b81e53dc)"))
      )
  }

  private val sessionAuthFailedUserNotExists = testM("Session authentication should fail if user does not exist") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid2.show), Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState()

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set(userSession))

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries)).either
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logEntries.get
    } yield assert(result)(isLeft(equalTo(Unauthorized("Invalid credentials")))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState)) &&
      assert(loggedMessages)(equalTo(Chain.one("Session authentication failed with: UserNotExists(431e092f-50ce-47eb-afbd-b806514d3f2c)")))
  }

  private val sessionAuthFailedNotActiveUser = testM("Session authentication should fail if user is not active") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid2.show), Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, Some(ExampleUserPasswordHash), confirmed = true, enabled = false))
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set(userSession))

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries)).either
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logEntries.get
    } yield assert(result)(isLeft(equalTo(Unauthorized("Invalid credentials")))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState)) &&
      assert(loggedMessages)(
        equalTo(Chain.one("Session authentication failed with: UserIsNotActive(431e092f-50ce-47eb-afbd-b806514d3f2c)"))
      )
  }

  private val sessionAuthFailedMissingCsrfToken = testM("Session authentication should fail if CSRF token is missing") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), None, Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, Some(ExampleUserPasswordHash), confirmed = true, enabled = true))
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set(userSession))

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries)).either
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logEntries.get
    } yield assert(result)(isLeft(equalTo(Unauthorized("Invalid credentials")))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState)) &&
      assert(loggedMessages)(equalTo(Chain.one("Session authentication failed with: InvalidCsrfToken()")))
  }

  private val sessionAuthFailedInvalidCsrfToken = testM("Session authentication should fail if CSRF token is invalid") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid3.show), Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, Some(ExampleUserPasswordHash), confirmed = true, enabled = true))
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set(userSession))

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries)).either
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logEntries.get
    } yield assert(result)(isLeft(equalTo(Unauthorized("Invalid credentials")))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState)) &&
      assert(loggedMessages)(
        equalTo(Chain.one("Session authentication failed with: InvalidCsrfToken(20e7cc77-cd3e-4c30-8e69-6bf543669c96)"))
      )
  }

  private val sessionAuthSuccessInvalidCsrfTokenSafeMethod =
    testM("Should authenticate user based on cookies with invalid CSRF token and safe method") {
      val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid3.show), Method.GET)

      val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

      val initUsersRepoState = UsersRepoFake.UsersRepoState(
        users = Set(ExampleUser),
        auth = Set(UserAuth(ExampleUserId, Some(ExampleUserPasswordHash), confirmed = true, enabled = true))
      )

      val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set(userSession))

      for {
        usersRepo        <- Ref.make(initUsersRepoState)
        sessionRepo      <- Ref.make(initSessionsRepoState)
        logEntries       <- Ref.make(Chain.empty[String])
        result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries))
        finalUserRepo    <- usersRepo.get
        finalSessionRepo <- sessionRepo.get
      } yield assert(result)(equalTo(SessionAuthenticatedUser(userSession))) &&
        assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
        assert(finalSessionRepo)(equalTo(initSessionsRepoState))
    }

  private val sessionAuthFailedExpired = testM("Session authentication should fail if session expired") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid2.show), Method.POST)

    val secondsInWeek = 60 * 60 * 24 * 7
    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(8 * secondsInWeek))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, Some(ExampleUserPasswordHash), confirmed = true, enabled = true))
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set(userSession))

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries)).either
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logEntries.get
    } yield assert(result)(isLeft(equalTo(Unauthorized("Invalid credentials")))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState)) &&
      assert(loggedMessages)(equalTo(Chain.one("Session authentication failed with: SessionExpired(e85630b6-fb45-4a1b-a963-5251b81e53dc)")))
  }

}
