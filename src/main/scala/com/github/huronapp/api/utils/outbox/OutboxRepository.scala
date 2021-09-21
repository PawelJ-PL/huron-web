package com.github.huronapp.api.utils.outbox

import com.github.huronapp.api.database.BasePostgresRepository
import doobie.util.transactor
import io.chrisdavenport.fuuid.FUUID
import io.getquill.Ord
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie.{Connection, tzio}
import zio.clock.Clock
import zio.{Has, ZIO, ZLayer}

import java.time.Instant

object OutboxRepository {

  type OutboxRepository = Has[OutboxRepository.Service]

  trait Service {

    def addCommand(id: FUUID, traceId: Option[String], command: OutboxCommand): ZIO[Connection, DbException, Unit]

    def getTasksAvailableOrAssignedBeforeDate(assignmentCutOff: Instant, limit: Int): ZIO[Connection, DbException, List[Task]]

    def markTasksAsAssigned(taskIds: List[FUUID]): ZIO[Connection, DbException, Unit]

    def markTaskDone(taskId: FUUID): ZIO[Connection, DbException, Unit]

    def markTaskFailed(taskId: FUUID, errorMessage: String): ZIO[Connection, DbException, Unit]

  }

  val postgres: ZLayer[Clock, Nothing, OutboxRepository] = ZLayer.fromService(clock =>
    new Service with BasePostgresRepository {
      import doobieContext._
      import dbImplicits._

      @annotation.nowarn("cat=unused")
      implicit val encodeCommand: MappedEncoding[OutboxCommand, String] = MappedEncoding[OutboxCommand, String](_.render)

      @annotation.nowarn("cat=unused")
      implicit val decodeCommand: MappedEncoding[String, OutboxCommand] = MappedEncoding[String, OutboxCommand](string =>
        OutboxCommand.fromString(string) match {
          case Left(error)  => throw error
          case Right(value) => value
        }
      )

      override def addCommand(id: FUUID, traceId: Option[String], command: OutboxCommand): ZIO[Connection, DbException, Unit] =
        for {
          now <- clock.instant
          entity = OutboxCommandEntity(id, traceId, command, None, 0, None, None, None, now)
          _   <- tzio(run(commands.insert(lift(entity))))
        } yield ()

      override def getTasksAvailableOrAssignedBeforeDate(
        assignmentCutOff: Instant,
        limit: Index
      ): ZIO[Has[transactor.Transactor[zio.Task]], DbException, List[Task]] =
        tzio(
          run(
            quote(
              commands
                .filter(c => c.assignedAt.forall(_ < lift(assignmentCutOff)) && c.finishedAt.isEmpty)
                .sortBy(c => (c.attempts, c.lastAttemptAt, c.createdAt))(Ord(Ord.asc, Ord.ascNullsFirst, Ord.asc))
                .take(lift(limit))
            )
          ).map(_.map(result => Task(result.id, result.command)))
        )

      override def markTasksAsAssigned(taskIds: List[FUUID]): ZIO[Connection, DbException, Unit] =
        for {
          now <- clock.instant
          _   <-
            tzio(run(quote(commands.filter(command => liftQuery(taskIds).contains(command.id)).update(_.assignedAt -> lift(Option(now))))))
        } yield ()

      override def markTaskDone(taskId: FUUID): ZIO[Has[transactor.Transactor[zio.Task]], DbException, Unit] =
        tzio(run(commands.filter(_.id == lift(taskId)).delete)).unit

      override def markTaskFailed(taskId: FUUID, errorMessage: String): ZIO[Has[transactor.Transactor[zio.Task]], DbException, Unit] =
        for {
          now <- clock.instant
          _   <- tzio(
                   run(
                     commands
                       .filter(_.id == lift(taskId))
                       .update(
                         t => t.attempts -> (t.attempts + 1L),
                         _.assignedAt -> lift(Option.empty[Instant]),
                         _.lastAttemptAt -> lift(Option(now)),
                         _.lastAttemptErrorMessage -> lift(Option(errorMessage))
                       )
                   )
                 )
        } yield ()

      private val commands = quote {
        querySchema[OutboxCommandEntity]("outbox_commands")
      }

    }
  )

}

private final case class OutboxCommandEntity(
  id: FUUID,
  traceId: Option[String],
  command: OutboxCommand,
  assignedAt: Option[Instant],
  attempts: Long,
  lastAttemptAt: Option[Instant],
  lastAttemptErrorMessage: Option[String],
  finishedAt: Option[Instant],
  createdAt: Instant)
