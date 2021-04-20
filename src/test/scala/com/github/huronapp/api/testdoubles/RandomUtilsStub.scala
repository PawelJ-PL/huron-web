package com.github.huronapp.api.testdoubles

import com.github.huronapp.api.constants.MiscConstants
import com.github.huronapp.api.utils.RandomUtils
import com.github.huronapp.api.utils.RandomUtils.RandomUtils
import io.chrisdavenport.fuuid.FUUID
import zio.{Ref, Task, URIO, ZIO, ZLayer}

import javax.xml.bind.DatatypeConverter

object RandomUtilsStub extends MiscConstants {

  final val RandomFuuids = List(FirstRandomFuuid, SecondRandomFuuid)

  private case class RandomState(fuuids: List[FUUID], nextRandomByte: Int)

  val create: ZLayer[Any, Nothing, RandomUtils] = Ref
    .make(RandomState(RandomFuuids, 0))
    .map(ref =>
      new RandomUtils.Service {

        private val nextByte = ref
          .getAndUpdate { prevState =>
            val next = if (prevState.nextRandomByte < 255) prevState.nextRandomByte + 1 else 0
            prevState.copy(nextRandomByte = next)
          }
          .map(_.nextRandomByte)

        override def secureBytes(length: Int): Task[Array[Byte]] =
          ZIO.foreach((1 to length).toList)(_ => nextByte).map(_.map(_.toByte).toArray)

        override def secureBytesHex(length: Int): Task[String] = secureBytes(length).map(DatatypeConverter.printHexBinary)

        override def randomFuuid: URIO[Any, FUUID] =
          ref
            .get
            .map(_.fuuids)
            .flatMap {
              case ::(head, next) =>
                ref.update(prev => prev.copy(fuuids = next)).as(head)
              case Nil            =>
                ZIO.fail(new RuntimeException("No more random FUUIDs"))
            }
            .orDie

      }
    )
    .toLayer

}
