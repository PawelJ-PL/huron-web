package com.github.huronapp.api.domain.users

import io.chrisdavenport.fuuid.FUUID

final case class UserContact(ownerId: FUUID, contactId: FUUID, alias: Option[String])
