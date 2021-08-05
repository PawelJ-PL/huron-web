package com.github.huronapp.api.auth.authentication

import com.github.huronapp.api.domain.users.{ApiKeyType, UserSession}
import io.chrisdavenport.fuuid.FUUID

sealed trait AuthenticatedUser {

  val userId: FUUID

}

object AuthenticatedUser {

  final case class SessionAuthenticatedUser(session: UserSession) extends AuthenticatedUser {

    override val userId: FUUID = session.userId

  }

  final case class ApiKeyUser(apiKeyId: FUUID, keyType: ApiKeyType, userId: FUUID) extends AuthenticatedUser

}
