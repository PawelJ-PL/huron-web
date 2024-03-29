package com.github.huronapp.api.testdoubles

import cats.syntax.eq._
import com.github.huronapp.api.auth.authentication.HttpAuthentication.HttpAuthentication
import com.github.huronapp.api.auth.authentication.{AuthenticatedUser, AuthenticationInputs}
import com.github.huronapp.api.constants.{MiscConstants, Users}
import com.github.huronapp.api.domain.users.{ApiKeyType, UserSession}
import com.github.huronapp.api.http.ErrorResponse
import org.http4s.Header
import org.typelevel.ci.CIStringSyntax
import zio.{ULayer, ZIO, ZLayer}

import java.time.Instant

object HttpAuthenticationFake extends Users with MiscConstants {

  val create: ULayer[HttpAuthentication] = ZLayer.succeed((inputs: AuthenticationInputs) =>
    if (inputs.apiKeyHeader.exists(_ === "UserOk"))
      ZIO.succeed(AuthenticatedUser.ApiKeyUser(ExampleFuuid1, ApiKeyType.Personal, ExampleUserId))
    else if (inputs.sessionCookieValue.exists(_ === "UserOk"))
      ZIO.succeed(AuthenticatedUser.SessionAuthenticatedUser(UserSession(ExampleFuuid1, ExampleUserId, ExampleFuuid2, Instant.EPOCH)))
    else
      ZIO.fail(ErrorResponse.Unauthorized("Invalid credentials"))
  )

  val validAuthHeader: Header.Raw = Header.Raw(ci"Cookie", "session=UserOk")

}
