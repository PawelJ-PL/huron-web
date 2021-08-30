package com.github.huronapp.api.utils.outbox

import com.github.huronapp.api.utils.RandomUtils
import com.github.huronapp.api.utils.RandomUtils.RandomUtils
import com.github.huronapp.api.utils.outbox.OutboxRepository.OutboxRepository
import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie.{Connection, Database}
import kamon.trace.Span
import zio.{Has, ZIO, ZLayer}
import zio.macros.accessible

@accessible
object OutboxService {

  type OutboxService = Has[OutboxService.Service]

  trait Service {

    def saveCommand(command: OutboxCommand): ZIO[Connection, DbException, FUUID]

    def finishTask(taskId: FUUID): ZIO[Any, DbException, Unit]

    def markTaskFailed(taskId: FUUID, errorMessage: String): ZIO[Any, DbException, Unit]

  }

  val live: ZLayer[OutboxRepository with RandomUtils with KamonTracing with Database.Database, Nothing, OutboxService] =
    ZLayer.fromServices[OutboxRepository.Service, RandomUtils.Service, KamonTracing.Service, Database.Service, OutboxService.Service](
      (outboxRepo, random, tracing, db) =>
        new Service {

          override def saveCommand(command: OutboxCommand): ZIO[Connection, DbException, FUUID] =
            for {
              commandId <- random.randomFuuid
              context   <- tracing.currentContext
              maybeTraceId = Option(context.get[Span](kamon.trace.Span.Key).trace.id.string)
              _         <- outboxRepo.addCommand(commandId, maybeTraceId, command)
            } yield commandId

          override def finishTask(taskId: FUUID): ZIO[Any, DbException, Unit] = db.transactionOrDie(outboxRepo.markTaskDone(taskId))

          override def markTaskFailed(taskId: FUUID, errorMessage: String): ZIO[Any, DbException, Unit] =
            db.transactionOrDie(outboxRepo.markTaskFailed(taskId, errorMessage))

        }
    )

}
