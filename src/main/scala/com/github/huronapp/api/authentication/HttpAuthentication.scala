package com.github.huronapp.api.authentication

import cats.syntax.eq._
import com.github.huronapp.api.authentication.AuthenticatedUser.{ApiKeyUser, SessionAuthenticatedUser}
import com.github.huronapp.api.authentication.SessionRepository.SessionRepository
import com.github.huronapp.api.config.SecurityConfig
import com.github.huronapp.api.domain.users.UsersRepository.UsersRepository
import com.github.huronapp.api.domain.users.{UserSession, UsersRepository}
import com.github.huronapp.api.http.ErrorResponse
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.Database
import sttp.model.Method.isSafe
import zio.clock.Clock
import zio.logging.{Logger, Logging}
import zio.macros.accessible
import zio.{Has, ZIO, ZLayer}

@accessible
object HttpAuthentication {

  type HttpAuthentication = Has[HttpAuthentication.Service]

  trait Service {

    def asUser(inputs: AuthenticationInputs): ZIO[Any, ErrorResponse, AuthenticatedUser]

  }

  val live: ZLayer[SessionRepository with UsersRepository with Has[doobie.Database.Service] with Logging with Clock with Has[
    SecurityConfig
  ], Nothing, Has[Service]] =
    ZLayer.fromServices[SessionRepository.Service, UsersRepository.Service, Database.Service, Logger[
      String
    ], Clock.Service, SecurityConfig, HttpAuthentication.Service]((sessionsRepo, usersRepo, db, logger, clock, securityConfig) =>
      new Service {

        override def asUser(inputs: AuthenticationInputs): ZIO[Any, ErrorResponse, AuthenticatedUser] =
          userFromApiKey(inputs).either.flatMap {
            case Left(apiKeyAuthError) =>
              sessionFromCookie(inputs).flatMapError(sessionAuthError =>
                logger
                  .info(s"Session auth failed with $sessionAuthError, API key auth failed with $apiKeyAuthError")
                  .as(ErrorResponse.Unauthorized("Invalid credentials"))
              )
            case Right(user)           => ZIO.succeed(user)
          }

        private def sessionFromCookie(inputs: AuthenticationInputs): ZIO[Any, CookieAuthenticationError, SessionAuthenticatedUser] =
          for {
            cookieValue <- ZIO.fromOption(inputs.sessionCookieValue).orElseFail(SessionCookieNotFound)
            asFuuid     <- ZIO.fromEither(FUUID.fromString(cookieValue)).mapError(err => NotValidSessionId(cookieValue, err.getMessage))
            session     <- sessionsRepo.getSession(asFuuid).orDie.some.orElseFail(SessionNotExists(asFuuid))
            userAuth    <- db.transactionOrDie(usersRepo.getAuthData(session.userId)).orDie.someOrFail(UserNotExists(session.userId))
            isActive    <- userAuth.isActive
            _           <- ZIO.cond(isActive, (), UserIsNotActive(session.userId))
            _           <- ZIO.cond(checkCsrfToken(session, inputs), (), InvalidCsrfToken(inputs.csrfToken.getOrElse("")))
            expireAt = session.createdAt.plusNanos(securityConfig.sessionCookieTtl.toNanos)
            now         <- clock.instant
            _           <- ZIO.cond(expireAt.isAfter(now), (), SessionExpired(session.sessionId))
          } yield SessionAuthenticatedUser(session)

        private def checkCsrfToken(session: UserSession, inputs: AuthenticationInputs): Boolean =
          if (isSafe(inputs.method)) true else inputs.csrfToken.exists(_ === session.csrfToken.show)

        private def userFromApiKey(inputs: AuthenticationInputs): ZIO[Any, ApiKeyAuthenticationError, ApiKeyUser] =
          for {
            apiKeyValue        <- ZIO.fromOption(inputs.apiKeyHeader.orElse(inputs.apiKeyQueryParam)).orElseFail(ApiKeyNotProvided)
            (userAuth, apiKey) <-
              db.transactionOrDie(usersRepo.getAuthWithApiKeyByKeyValue(apiKeyValue).orDie.someOrFail(ApiKeyNotFound(apiKeyValue)))
            isActive           <- userAuth.isActive
            _                  <- ZIO.cond(isActive, (), UserIsNotActive(userAuth.userId))
            _                  <- ZIO.cond(apiKey.enabled, (), ApiKeyDisabled(apiKey.id))
            now                <- clock.instant
            _                  <- apiKey.validTo match {
                                    case Some(validTo) => ZIO.cond(validTo.isAfter(now), (), ApiKeyExpired(apiKey.id, validTo))
                                    case None          => ZIO.unit
                                  }
          } yield ApiKeyUser(apiKey.id, apiKey.keyType, apiKey.userId)

      }
    )

}
