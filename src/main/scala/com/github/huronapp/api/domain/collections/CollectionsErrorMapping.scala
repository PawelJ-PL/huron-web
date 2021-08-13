package com.github.huronapp.api.domain.collections

import com.github.huronapp.api.http.ErrorResponse

object CollectionsErrorMapping {

  def getCollectionDetailsError(error: GetCollectionDetailsError): ErrorResponse =
    error match {
      case _: AuthorizationError => ErrorResponse.Forbidden("Operation not permitted")
      case _: CollectionNotFound => ErrorResponse.Forbidden("Operation not permitted")
    }

  def getEncryptionKeyError(error: GetEncryptionKeyError): ErrorResponse =
    error match {
      case _: AuthorizationError => ErrorResponse.Forbidden("Operation not permitted")
    }

}
