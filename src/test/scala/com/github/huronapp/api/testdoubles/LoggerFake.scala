package com.github.huronapp.api.testdoubles

import cats.data.Chain
import zio.logging.{LogContext, Logger, Logging}
import zio.{Ref, UIO, ULayer, ZIO, ZLayer}

object LoggerFake {

  def usingRef(entries: Ref[Chain[String]]): ULayer[Logging] =
    ZLayer.succeed(new Logger[String] {

      override def locally[R1, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] = zio

      override def log(line: => String): UIO[Unit] = entries.update(_.append(line))

      override def logContext: UIO[LogContext] = ???

    })

}
