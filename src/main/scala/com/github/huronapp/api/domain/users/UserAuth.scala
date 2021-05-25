package com.github.huronapp.api.domain.users

import com.github.huronapp.api.utils.crypto.Crypto.{Crypto, verifyBcryptPassword}
import io.chrisdavenport.fuuid.FUUID
import zio.{UIO, ZIO}

final case class UserAuth(userId: FUUID, passwordHash: String, confirmed: Boolean, enabled: Boolean) {

  val isActive: UIO[Boolean] = UIO.succeed(confirmed && enabled)

  def checkPassword(password: Array[Byte]): ZIO[Crypto, Throwable, Boolean] = verifyBcryptPassword(passwordHash, password)

}
