package com.github.huronapp.api.authentication

import cats.syntax.eq._
import org.http4s.headers.Cookie
import sttp.model.HeaderNames.{Cookie => CookieHeader}
import sttp.model.Method
import sttp.tapir._
import sttp.tapir.model.ServerRequest

case class AuthenticationInputs(sessionCookieValue: Option[String], csrfToken: Option[String], method: Method)

object TapirAuthenticationInputs {

  val authRequestParts: EndpointInput[AuthenticationInputs] =
    extractFromRequest[Option[String]](sessionCookieFromRequest)
      .and(header[Option[String]]("X-Csrf-Token"))
      .and(extractFromRequest[Method](_.method))
      .mapTo(AuthenticationInputs)

  private def sessionCookieFromRequest(req: ServerRequest): Option[String] =
    req
      .header(CookieHeader)
      .flatMap(cookieValue => Cookie.parse(cookieValue).toOption)
      .map(_.values)
      .flatMap(_.find(_.name === "session"))
      .map(_.content)

}
