package com.github.huronapp.api.messagebus.handlers

import cats.syntax.show._
import com.github.huronapp.api.utils.FileSystemService
import com.github.huronapp.api.utils.FileSystemService.FileSystemService
import com.github.huronapp.api.utils.outbox.OutboxService.OutboxService
import com.github.huronapp.api.utils.outbox.{OutboxCommand, OutboxService, Task}
import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.DbException
import zio.clock.Clock
import zio.logging.{Logging, log}
import zio.stream.{ZSink, ZStream}
import zio.{Has, Queue, URIO, ZIO, ZManaged}

import java.util.concurrent.TimeoutException

object OutboxTasksHandler {

  val handle
    : ZManaged[Has[Queue[Task]] with Logging with OutboxService with FileSystemService with Clock with KamonTracing, Nothing, Unit] =
    ZManaged.accessM[Has[Queue[Task]] with Logging with OutboxService with FileSystemService with Clock with KamonTracing] { env =>
      ZStream.fromQueue(env.get[Queue[Task]]).run(ZSink.foreach(process))
    }

  def process(task: Task): ZIO[Logging with OutboxService with FileSystemService with Clock with KamonTracing, Nothing, Unit] =
    KamonTracing
      .preserveContext(
        KamonTracing.createSpan(
          "Processing outbox task",
          performTask(task.command).tapError(error => reportTaskFailed(task.taskId, error)) *> reportTaskFinished(task),
          Map("task.id" -> task.taskId.show)
        )
      )
      .timeoutFail(new TimeoutException())(java.time.Duration.ofMinutes(2))
      .resurrect
      .catchAll(err => log.throwable("Unable to process outbox task", err))

  private def reportTaskFinished(task: Task): ZIO[Logging with OutboxService, DbException, Unit] =
    OutboxService.finishTask(task.taskId) *> log.debug(show"Outbox task ${task.taskId}")

  private def reportTaskFailed(taskId: FUUID, error: Throwable): URIO[OutboxService, Unit] =
    OutboxService.markTaskFailed(taskId, error.getMessage).orDie

  private def performTask(command: OutboxCommand): ZIO[FileSystemService with KamonTracing, Throwable, Unit] =
    command match {
      case OutboxCommand.DeleteFiles(paths, recursively) =>
        KamonTracing.createSpan(
          "Delete file or dir task",
          ZIO.foreach_(paths)(path => FileSystemService.deleteFileOrDirectory(path, recursively)),
          Map.empty
        )
    }

}
