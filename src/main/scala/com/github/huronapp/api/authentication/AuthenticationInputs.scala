package com.github.huronapp.api.authentication

import cats.syntax.eq._
import org.http4s.headers.Cookie
import sttp.model.HeaderNames.{Cookie => CookieHeader}
import sttp.model.Method
import sttp.tapir._
import sttp.tapir.model.ServerRequest

case class AuthenticationInputs(
  sessionCookieValue: Option[String],
  csrfToken: Option[String],
  apiKeyHeader: Option[String],
  apiKeyQueryParam: Option[String],
  method: Method)

object TapirAuthenticationInputs {

  val authRequestParts: EndpointInput[AuthenticationInputs] =
    extractFromRequest[Option[String]](sessionCookieFromRequest)
      .and(header[Option[String]]("X-Csrf-Token"))
      .and(auth.apiKey(header[Option[String]]("X-Api-Key")).securitySchemeName("apiKeyHeader"))
      .and(auth.apiKey(query[Option[String]]("api-key")).securitySchemeName("apiKeyQueryParam"))
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
