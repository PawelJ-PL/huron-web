package com.github.huronapp.api.utils.tracing

import kamon.Kamon
import kamon.context.Context
import kamon.tag.TagSet
import zio.{Has, Managed, UIO, ULayer, ZIO, ZLayer}

object KamonTracing {

  type KamonTracing = Has[KamonTracing.Service]

  trait Service {

    def currentContext: UIO[Context]

    def storeContext(newContext: Context): Managed[Nothing, Unit]

    def createSpan[R, E, A](name: String, operation: ZIO[R, E, A], tags: Map[String, Any] = Map.empty): ZIO[R, E, A]

    def preserveContext[R, E, A](operation: ZIO[R, E, A]): ZIO[R, E, A]

    def withContext[R, E, A](operation: ZIO[R, E, A], context: Context): ZIO[R, E, A]

  }

  val live: ULayer[KamonTracing] = ZLayer.succeed(new Service {

    override def currentContext: UIO[Context] = UIO(Kamon.currentContext())

    override def storeContext(newContext: Context): Managed[Nothing, Unit] =
      Managed.suspend {
        currentContext
          .toManaged_
          .flatMap(rootContext => Managed.make(UIO(Kamon.storeContext(newContext)).unit)(_ => UIO(Kamon.storeContext(rootContext)).unit))
      }

    override def createSpan[R, E, A](name: String, operation: ZIO[R, E, A], tags: Map[String, Any]): ZIO[R, E, A] =
      (for {
        span    <- Managed.make(UIO(Kamon.spanBuilder(name).tag(TagSet.from(tags)).start()))(span => UIO(span.finish()))
        context <- currentContext.map(ctx => ctx.withEntry(kamon.trace.Span.Key, span)).toManaged_
        _       <- storeContext(context)
      } yield span).use(span =>
        operation.catchAllDefect { e =>
          span.fail(e)
          ZIO.die(e)
        }
      )

    override def preserveContext[R, E, A](operation: ZIO[R, E, A]): ZIO[R, E, A] =
      currentContext.flatMap(ctx => operation.ensuring(UIO(Kamon.storeContext(ctx))))

    override def withContext[R, E, A](operation: ZIO[R, E, A], context: Context): ZIO[R, E, A] =
      ZIO.effectTotal(Kamon.runWithContext(context)(operation)).flatten

  })

}
