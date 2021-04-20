package com.github.huronapp.api.authentication

import com.github.huronapp.api.domain.users.UserSession
import io.chrisdavenport.fuuid.FUUID

sealed trait AuthenticatedUser {

  val userId: FUUID

}

object AuthenticatedUser {

  final case class SessionAuthenticatedUser(session: UserSession) extends AuthenticatedUser {

    override val userId: FUUID = session.userId

  }

}
