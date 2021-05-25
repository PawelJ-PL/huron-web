package com.github.huronapp.api.authentication

import cats.data.Chain
import com.github.huronapp.api.authentication.AuthenticatedUser.{ApiKeyUser, SessionAuthenticatedUser}
import com.github.huronapp.api.authentication.HttpAuthentication.HttpAuthentication
import com.github.huronapp.api.constants.{Config, MiscConstants, Users}
import com.github.huronapp.api.domain.users.{ApiKeyType, UserAuth, UserSession}
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
      authFailedNoCookieNoApiKey,
      sessionAuthFailedInvalidSessionId,
      sessionAuthFailedNoSession,
      sessionAuthFailedUserNotExists,
      sessionAuthFailedNotActiveUser,
      sessionAuthFailedMissingCsrfToken,
      sessionAuthFailedInvalidCsrfToken,
      sessionAuthSuccessInvalidCsrfTokenSafeMethod,
      sessionAuthFailedExpired,
      headerApiKeyAuthSuccess,
      queryParamApiKeyAuthSuccess,
      apiKeyAuthFailedKeyNotFound,
      apiKeyAuthFailedUserNotActive,
      apiKeyAuthFailedKeyDisabled,
      apiKeyAuthFailedKeyExpired,
      apiKeyAuthSuccessNotExpiredKey,
      apiKeyBeforeCookie
    )

  private val sessionAuthSuccess = testM("Should authenticate user based on cookies") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid2.show), None, None, Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
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

  private val authFailedNoCookieNoApiKey = testM("Authentication should fail if no session cookie and API key set") {
    val inputs = AuthenticationInputs(None, Some(ExampleFuuid2.show), None, None, Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
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
        equalTo(Chain.one("Session auth failed with SessionCookieNotFound, API key auth failed with ApiKeyNotProvided"))
      )
  }

  private val sessionAuthFailedInvalidSessionId = testM("Session authentication should fail if session ID is not valid uuid") {
    val inputs = AuthenticationInputs(Some("Foo"), Some(ExampleFuuid2.show), None, None, Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
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
        equalTo(
          Chain.one("Session auth failed with NotValidSessionId(Foo,Invalid UUID string: Foo), API key auth failed with ApiKeyNotProvided")
        )
      )
  }

  private val sessionAuthFailedNoSession = testM("Session authentication should fail if session not found") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid2.show), None, None, Method.POST)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
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
        equalTo(
          Chain.one(
            "Session auth failed with SessionNotExists(e85630b6-fb45-4a1b-a963-5251b81e53dc), API key auth failed with ApiKeyNotProvided"
          )
        )
      )
  }

  private val sessionAuthFailedUserNotExists = testM("Session authentication should fail if user does not exist") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid2.show), None, None, Method.POST)

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
      assert(loggedMessages)(
        equalTo(
          Chain.one(
            "Session auth failed with UserNotExists(431e092f-50ce-47eb-afbd-b806514d3f2c), API key auth failed with ApiKeyNotProvided"
          )
        )
      )
  }

  private val sessionAuthFailedNotActiveUser = testM("Session authentication should fail if user is not active") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid2.show), None, None, Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = false))
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
        equalTo(
          Chain.one(
            "Session auth failed with UserIsNotActive(431e092f-50ce-47eb-afbd-b806514d3f2c), API key auth failed with ApiKeyNotProvided"
          )
        )
      )
  }

  private val sessionAuthFailedMissingCsrfToken = testM("Session authentication should fail if CSRF token is missing") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), None, None, None, Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
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
      assert(loggedMessages)(equalTo(Chain.one("Session auth failed with InvalidCsrfToken(), API key auth failed with ApiKeyNotProvided")))
  }

  private val sessionAuthFailedInvalidCsrfToken = testM("Session authentication should fail if CSRF token is invalid") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid3.show), None, None, Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
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
        equalTo(
          Chain.one(
            "Session auth failed with InvalidCsrfToken(20e7cc77-cd3e-4c30-8e69-6bf543669c96), API key auth failed with ApiKeyNotProvided"
          )
        )
      )
  }

  private val sessionAuthSuccessInvalidCsrfTokenSafeMethod =
    testM("Should authenticate user based on cookies with invalid CSRF token and safe method") {
      val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid3.show), None, None, Method.GET)

      val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

      val initUsersRepoState = UsersRepoFake.UsersRepoState(
        users = Set(ExampleUser),
        auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
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
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid2.show), None, None, Method.POST)

    val secondsInWeek = 60 * 60 * 24 * 7
    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(8L * secondsInWeek))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
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
        equalTo(
          Chain.one(
            "Session auth failed with SessionExpired(e85630b6-fb45-4a1b-a963-5251b81e53dc), API key auth failed with ApiKeyNotProvided"
          )
        )
      )
  }

  private val headerApiKeyAuthSuccess = testM("Should authenticate user based on API key header") {
    val inputs = AuthenticationInputs(None, None, Some(ExampleApiKey.key), None, Method.POST)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
      apiKeys = Set(ExampleApiKey)
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set.empty)

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries))
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result)(equalTo(ApiKeyUser(ExampleApiKeyId, ApiKeyType.Personal, ExampleUserId))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState))
  }

  private val queryParamApiKeyAuthSuccess = testM("Should authenticate user based on API key query parameter") {
    val inputs = AuthenticationInputs(None, None, None, Some(ExampleApiKey.key), Method.POST)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
      apiKeys = Set(ExampleApiKey)
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set.empty)

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries))
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result)(equalTo(ApiKeyUser(ExampleApiKeyId, ApiKeyType.Personal, ExampleUserId))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState))
  }

  private val apiKeyAuthFailedKeyNotFound = testM("API key authentication should fail if api key not find in repository") {
    val inputs = AuthenticationInputs(None, None, Some(ExampleApiKey.key), None, Method.POST)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
      apiKeys = Set.empty
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set.empty)

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
        equalTo(
          Chain.one(
            "Session auth failed with SessionCookieNotFound, API key auth failed with ApiKeyNotFound(ABCD)"
          )
        )
      )
  }

  private val apiKeyAuthFailedUserNotActive = testM("API key authentication should fail if user is not active") {
    val inputs = AuthenticationInputs(None, None, Some(ExampleApiKey.key), None, Method.POST)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = false)),
      apiKeys = Set(ExampleApiKey)
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set.empty)

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
        equalTo(
          Chain.one(
            "Session auth failed with SessionCookieNotFound, API key auth failed with UserIsNotActive(431e092f-50ce-47eb-afbd-b806514d3f2c)"
          )
        )
      )
  }

  private val apiKeyAuthFailedKeyDisabled = testM("API key authentication should fail if key is disabled") {
    val inputs = AuthenticationInputs(None, None, Some(ExampleApiKey.key), None, Method.POST)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
      apiKeys = Set(ExampleApiKey.copy(enabled = false))
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set.empty)

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
        equalTo(
          Chain.one(
            "Session auth failed with SessionCookieNotFound, API key auth failed with ApiKeyDisabled(b0edc95b-5bf8-4be1-b272-e91dd391beee)"
          )
        )
      )
  }

  private val apiKeyAuthFailedKeyExpired = testM("API key authentication should fail if key expired") {
    val inputs = AuthenticationInputs(None, None, Some(ExampleApiKey.key), None, Method.POST)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
      apiKeys = Set(ExampleApiKey.copy(validTo = Some(Instant.EPOCH.minusSeconds(1200))))
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set.empty)

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
        equalTo(
          Chain.one(
            "Session auth failed with SessionCookieNotFound, API key auth failed with ApiKeyExpired(b0edc95b-5bf8-4be1-b272-e91dd391beee,1969-12-31T23:40:00Z)"
          )
        )
      )
  }

  private val apiKeyAuthSuccessNotExpiredKey = testM("API key should authenticate user if key is still valid") {
    val inputs = AuthenticationInputs(None, None, Some(ExampleApiKey.key), None, Method.POST)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
      apiKeys = Set(ExampleApiKey.copy(validTo = Some(Instant.EPOCH.plusSeconds(1200))))
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set.empty)

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries))
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
      loggedMessages   <- logEntries.get
    } yield assert(result)(equalTo(ApiKeyUser(ExampleApiKeyId, ExampleApiKey.keyType, ExampleUserId))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState)) &&
      assert(loggedMessages)(equalTo(Chain.empty))
  }

  private val apiKeyBeforeCookie = testM("API key should be taken before cookie") {
    val inputs = AuthenticationInputs(Some(ExampleFuuid1.show), Some(ExampleFuuid2.show), Some(ExampleApiKey.key), None, Method.POST)

    val userSession = UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH.minusSeconds(180))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(
      users = Set(ExampleUser),
      auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
      apiKeys = Set(ExampleApiKey)
    )

    val initSessionsRepoState = SessionRepoFake.SessionRepoState(sessions = Set(userSession))

    for {
      usersRepo        <- Ref.make(initUsersRepoState)
      sessionRepo      <- Ref.make(initSessionsRepoState)
      logEntries       <- Ref.make(Chain.empty[String])
      result           <- HttpAuthentication.asUser(inputs).provideLayer(authentication(usersRepo, sessionRepo, logEntries))
      finalUserRepo    <- usersRepo.get
      finalSessionRepo <- sessionRepo.get
    } yield assert(result)(equalTo(ApiKeyUser(ExampleApiKeyId, ExampleApiKey.keyType, ExampleUserId))) &&
      assert(finalUserRepo)(equalTo(initUsersRepoState)) &&
      assert(finalSessionRepo)(equalTo(initSessionsRepoState))
  }

}
