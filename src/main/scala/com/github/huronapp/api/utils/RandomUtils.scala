package com.github.huronapp.api.utils

import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import io.chrisdavenport.fuuid.FUUID
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{Has, Runtime, Task, URIO, ZIO, ZLayer}
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

  val live: ZLayer[Blocking with KamonTracing with Clock, Nothing, RandomUtils] =
    ZLayer.fromServices[Blocking.Service, KamonTracing.Service, Clock.Service, RandomUtils.Service]((blocking, tracing, clock) =>
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

        override val randomFuuid: URIO[Any, FUUID] =
          ZIO
            .runtime[Clock with Blocking]
            .flatMap { implicit r: Runtime[Clock with Blocking] => FUUID.randomFUUID[Task].orDie }
            .provideLayer(ZLayer.succeed(clock) ++ ZLayer.succeed(blocking))

      }
    )

}
