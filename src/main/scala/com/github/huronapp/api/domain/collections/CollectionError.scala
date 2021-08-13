package com.github.huronapp.api.domain.collections

import cats.syntax.show._
import com.github.huronapp.api.auth.authorization.{AuthorizationError => AuthError}
import io.chrisdavenport.fuuid.FUUID

sealed trait CollectionError {

  val logMessage: String

}

sealed trait GetCollectionDetailsError extends CollectionError

sealed trait GetEncryptionKeyError extends CollectionError

final case class AuthorizationError(error: AuthError) extends GetCollectionDetailsError with GetEncryptionKeyError {

  override val logMessage: String = error.message

}

final case class CollectionNotFound(collectionId: FUUID) extends GetCollectionDetailsError {

  override val logMessage: String = show"Collection $collectionId not found"

}
