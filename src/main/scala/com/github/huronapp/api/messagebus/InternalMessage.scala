package com.github.huronapp.api.messagebus

import com.github.huronapp.api.domain.users.{Email, User}
import kamon.context.Context

sealed trait InternalMessage {

  val tracingContext: Option[Context]

}

object InternalMessage {

  final case class UserRegistered(user: User, email: Email, registrationToken: String, tracingContext: Option[Context] = None)
      extends InternalMessage

  final case class PasswordResetRequested(user: User, email: Email, passwordResetToken: String, tracingContext: Option[Context] = None)
      extends InternalMessage

}
