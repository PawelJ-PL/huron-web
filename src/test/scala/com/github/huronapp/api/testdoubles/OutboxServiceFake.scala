package com.github.huronapp.api.testdoubles

import cats.syntax.eq._
import com.github.huronapp.api.constants.MiscConstants
import com.github.huronapp.api.utils.outbox.OutboxService.OutboxService
import com.github.huronapp.api.utils.outbox.{OutboxCommand, OutboxService, Task}
import doobie.util.transactor
import io.chrisdavenport.fuuid.FUUID
import io.github.gaelrenoux.tranzactio.DbException
import zio.{Has, Ref, ULayer, ZIO, ZLayer}

object OutboxServiceFake {

  def create(outboxState: Ref[List[StoredTask]]): ULayer[OutboxService] =
    ZLayer.succeed(new OutboxService.Service with MiscConstants {

      override def saveCommand(command: OutboxCommand): ZIO[Has[transactor.Transactor[zio.Task]], DbException, FUUID] =
        outboxState.update(state => state :+ StoredTask(Task(ExampleFuuid1, command), NotStarted)).as(ExampleFuuid1)

      override def finishTask(taskId: FUUID): ZIO[Any, DbException, Unit] = outboxState.update(_.filterNot(_.task.taskId === taskId))

      override def markTaskFailed(taskId: FUUID, errorMessage: String): ZIO[Any, DbException, Unit] =
        outboxState.update { prev =>
          val updated = prev.find(_.task.taskId === taskId).map(t => t.copy(state = Failed(errorMessage)))
          updated.map(t => prev.filterNot(_.task.taskId === taskId) :+ t).getOrElse(prev)
        }

    })

}

final case class StoredTask(task: Task, state: TaskState)

sealed trait TaskState

case object NotStarted extends TaskState

final case class Failed(message: String) extends TaskState
