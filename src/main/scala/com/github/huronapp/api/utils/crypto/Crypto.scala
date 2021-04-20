package com.github.huronapp.api.utils.crypto

import com.github.huronapp.api.config.SecurityConfig
import com.github.huronapp.api.utils.RandomUtils
import com.github.huronapp.api.utils.RandomUtils.RandomUtils
import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import org.bouncycastle.crypto.generators.OpenBSDBCrypt
import zio.macros.accessible
import zio.{Has, Task, UIO, ZIO, ZLayer}

import javax.xml.bind.DatatypeConverter

@accessible
object Crypto {

  type Crypto = Has[Crypto.Service]

  trait Service {

    def digest[A <: DigestAlgo](plainText: String, algorithm: A): UIO[String]

    def bcryptGenerate(password: Array[Byte]): Task[String]

    def verifyBcryptPassword(hash: String, password: Array[Byte]): Task[Boolean]

  }

  val live: ZLayer[RandomUtils with Has[SecurityConfig] with KamonTracing, Nothing, Crypto] =
    ZLayer.fromServices[RandomUtils.Service, SecurityConfig, KamonTracing.Service, Service]((random, config, tracing) =>
      new Service {

        override def digest[A <: DigestAlgo](plainText: String, algorithm: A): UIO[String] = {
          val bytes = algorithm.instance.digest(plainText.getBytes)
          ZIO.succeed(DatatypeConverter.printHexBinary(bytes))
        }

        override def bcryptGenerate(password: Array[Byte]): Task[String] =
          tracing.createSpan(
            "Generate bcrypt hash",
            for {
              salt   <- random.secureBytes(16)
              result <- Task(OpenBSDBCrypt.generate(password, salt, config.bcryptRounds))
            } yield result
          )

        override def verifyBcryptPassword(hash: String, password: Array[Byte]): Task[Boolean] =
          tracing.createSpan("Verify bcrypt hash", Task(OpenBSDBCrypt.checkPassword(hash, password)))

      }
    )

}
