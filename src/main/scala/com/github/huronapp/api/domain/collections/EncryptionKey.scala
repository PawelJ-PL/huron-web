package com.github.huronapp.api.domain.collections

import io.chrisdavenport.fuuid.FUUID

final case class EncryptionKey(collectionId: FUUID, userId: FUUID, key: String, version: FUUID)
