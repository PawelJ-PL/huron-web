package com.github.huronapp.api.domain.users

import io.chrisdavenport.fuuid.circe._
import io.chrisdavenport.fuuid.FUUID
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.Instant

final case class UserSession(sessionId: FUUID, userId: FUUID, csrfToken: FUUID, createdAt: Instant)

object UserSession {

  implicit val codec: Codec[UserSession] = deriveCodec[UserSession]

}
