package com.github.huronapp.api.testdoubles

import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import kamon.context.Context
import zio.{Managed, UIO, ULayer, ZIO, ZLayer}

object KamonTracingFake {

  val noOp: ULayer[KamonTracing] = ZLayer.succeed(new KamonTracing.Service {

    override def currentContext: UIO[Context] = UIO(Context.Empty)

    override def storeContext(newContext: Context): Managed[Nothing, Unit] = ???

    override def createSpan[R, E, A](name: String, operation: ZIO[R, E, A], tags: Map[String, Any]): ZIO[R, E, A] = operation

    override def preserveContext[R, E, A](operation: ZIO[R, E, A]): ZIO[R, E, A] = ???

    override def withContext[R, E, A](operation: ZIO[R, E, A], context: Context): ZIO[R, E, A] = operation

  })

}
