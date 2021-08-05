package com.github.huronapp.api.auth.authorization

sealed trait AuthorizationError {

  val message: String

}

final case class OperationNotPermitted(operation: AuthorizedOperation) extends AuthorizationError {

  override val message: String = operation.failureMessage

}
