package com.github.huronapp.api.constants

import com.github.huronapp.api.domain.collections.Collection
import io.chrisdavenport.fuuid.FUUID

trait Collections {

  final val ExampleCollectionId = FUUID.fuuid("86edda81-9c7b-474e-80b7-aa796ded6a7b")

  final val ExampleEncryptionKey = "SecretKey"

  final val ExampleEncryptionKeyVersion = FUUID.fuuid("0afd4b70-cb86-4523-bc48-7a718a0a68ef")

  final val ExampleCollectionName = "My collection"

  final val ExampleCollection = Collection(ExampleCollectionId, ExampleCollectionName, ExampleEncryptionKeyVersion)

}
