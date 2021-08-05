package com.github.huronapp.api.auth.authentication

import io.chrisdavenport.fuuid.FUUID

import java.time.Instant

sealed trait AuthenticationError

sealed trait CookieAuthenticationError extends AuthenticationError

sealed trait ApiKeyAuthenticationError extends AuthenticationError

case object SessionCookieNotFound extends CookieAuthenticationError

final case class NotValidSessionId(id: String, error: String) extends CookieAuthenticationError

final case class SessionNotExists(sessionId: FUUID) extends CookieAuthenticationError

final case class UserNotExists(userId: FUUID) extends CookieAuthenticationError

final case class UserIsNotActive(userId: FUUID) extends CookieAuthenticationError with ApiKeyAuthenticationError

final case class InvalidCsrfToken(token: String) extends CookieAuthenticationError

final case class SessionExpired(sessionId: FUUID) extends CookieAuthenticationError

case object ApiKeyNotProvided extends ApiKeyAuthenticationError

final case class ApiKeyNotFound(keyValue: String) extends ApiKeyAuthenticationError

final case class ApiKeyDisabled(keyId: FUUID) extends ApiKeyAuthenticationError

final case class ApiKeyExpired(keyId: FUUID, validTo: Instant) extends ApiKeyAuthenticationError
