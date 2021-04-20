package com.github.huronapp.api.utils

import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import io.chrisdavenport.fuuid.FUUID
import zio.blocking.Blocking
import zio.{Has, Task, URIO, ZLayer}
import zio.interop.catz._

import java.security.SecureRandom
import javax.xml.bind.DatatypeConverter

object RandomUtils {

  type RandomUtils = Has[RandomUtils.Service]

  trait Service {

    def secureBytes(length: Int): Task[Array[Byte]]

    def secureBytesHex(length: Int): Task[String]

    def randomFuuid: URIO[Any, FUUID]

  }

  val live: ZLayer[Blocking with KamonTracing, Nothing, RandomUtils] =
    ZLayer.fromServices[Blocking.Service, KamonTracing.Service, RandomUtils.Service]((blocking, tracing) =>
      new Service {

        override def secureBytes(length: Int): Task[Array[Byte]] =
          tracing.createSpan(
            "Secure random bytes",
            blocking.effectBlocking {
              val bytes = new Array[Byte](length)
              new SecureRandom().nextBytes(bytes)
              bytes
            }
          )

        override def secureBytesHex(length: Int): Task[String] = secureBytes(length).map(DatatypeConverter.printHexBinary)

        override val randomFuuid: URIO[Any, FUUID] = FUUID.randomFUUID[Task].orDie

      }
    )

}
