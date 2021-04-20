package com.github.huronapp.api.testdoubles

import cats.syntax.eq._
import com.github.huronapp.api.utils.crypto.Crypto.Crypto
import com.github.huronapp.api.utils.crypto.{Crypto, DigestAlgo}
import zio.{Task, UIO, ULayer, ZIO, ZLayer}

object CryptoStub {

  val create: ULayer[Crypto] = ZLayer.succeed(new Crypto.Service {

    override def digest[A <: DigestAlgo](plainText: String, algorithm: A): UIO[String] = UIO(s"digest($plainText)")

    override def bcryptGenerate(password: Array[Byte]): Task[String] = ZIO.succeed(s"bcrypt(${new String(password)})")

    override def verifyBcryptPassword(hash: String, password: Array[Byte]): Task[Boolean] =
      ZIO.succeed(hash === s"bcrypt(${new String(password)})")

  })

}
