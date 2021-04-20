package com.github.huronapp.api.authentication

import io.chrisdavenport.fuuid.FUUID

sealed trait AuthenticationError

sealed trait CookieAuthenticationError extends AuthenticationError

case object SessionCookieNotFound extends CookieAuthenticationError

final case class NotValidSessionId(id: String, error: String) extends CookieAuthenticationError

final case class SessionNotExists(sessionId: FUUID) extends CookieAuthenticationError

final case class UserNotExists(userId: FUUID) extends CookieAuthenticationError

final case class UserIsNotActive(userId: FUUID) extends CookieAuthenticationError

final case class InvalidCsrfToken(token: String) extends CookieAuthenticationError

final case class SessionExpired(sessionId: FUUID) extends CookieAuthenticationError
