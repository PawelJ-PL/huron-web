package com.github.huronapp.api.scheduler

import cats.syntax.show._
import com.github.huronapp.api.config.OutboxTasksConfig
import com.github.huronapp.api.utils.outbox.OutboxRepository.OutboxRepository
import com.github.huronapp.api.utils.outbox.{OutboxRepository, Task}
import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import io.github.gaelrenoux.tranzactio.doobie.Database
import zio.clock.Clock
import zio.logging.{Logger, Logging}
import zio.{Has, Queue, Schedule, ZIO, ZLayer}

object OutboxTasksDispatcher {

  type OutboxTasksDispatcher = Has[OutboxTasksDispatcher.Service]

  trait Service {

    val start: ZIO[Any, Nothing, Unit]

  }

  val start: ZIO[OutboxTasksDispatcher, Nothing, Unit] = ZIO.accessM[OutboxTasksDispatcher](_.get.start)

  val live: ZLayer[Has[Queue[Task]] with Logging with Has[
    OutboxTasksConfig
  ] with Clock with Database.Database with OutboxRepository with KamonTracing, Nothing, OutboxTasksDispatcher] =
    ZLayer.fromServices[Queue[Task], Logger[
      String
    ], OutboxTasksConfig, Clock.Service, Database.Service, OutboxRepository.Service, KamonTracing.Service, OutboxTasksDispatcher.Service](
      (queue, logger, dispatcherConfig, clock, db, outboxRepo, tracing) =>
        new Service {

          private val dispatchTasks: ZIO[Any, Throwable, Unit] = tracing
            .createSpan(
              "Dispatch outbox task",
              db
                .transactionOrDie(
                  for {
                    queueSize <- queue.size
                    limit = queue.capacity - queueSize
                    now       <- clock.instant
                    cutoffTime = now.minus(dispatcherConfig.taskDurationCutoff)
                    tasks     <- outboxRepo.getTasksAvailableOrAssignedBeforeDate(cutoffTime, limit)
                    _         <- if (tasks.nonEmpty) logger.debug(show"Dispatching following outbox tasks: ${tasks.map(_.taskId).mkString(", ")}")
                                 else logger.debug("No outbox task to dispatch")
                    _         <- queue.offerAll(tasks).when(tasks.nonEmpty)
                    _         <- outboxRepo.markTasksAsAssigned(tasks.map(_.taskId)).when(tasks.nonEmpty)
                  } yield ()
                ),
              Map.empty
            )
            .resurrect

          override val start: ZIO[Any, Nothing, Unit] = dispatchTasks
            .catchAll(err => logger.throwable("Outbox tasks processing failed", err))
            .repeat(Schedule.forever && Schedule.windowed(dispatcherConfig.runEvery))
            .provideLayer(ZLayer.succeed(clock))
            .unit

        }
    )

}
