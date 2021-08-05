package com.github.huronapp.api.domain.collections

import io.chrisdavenport.fuuid.FUUID

final case class Collection(id: FUUID, name: String, encryptionKeyVersion: FUUID)
