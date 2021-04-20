package com.github.huronapp.api.scheduler

import cats.syntax.show._
import com.github.huronapp.api.config.{RegistrationCleanupConfig, SecurityConfig}
import com.github.huronapp.api.domain.users.UsersRepository
import com.github.huronapp.api.domain.users.UsersRepository.UsersRepository
import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.Database
import zio.clock.Clock
import zio.{Has, Schedule, ZIO, ZLayer}
import zio.logging.{Logger, Logging}

import java.time.temporal.ChronoUnit

object RegistrationCleaner {

  type RegistrationCleaner = Has[RegistrationCleaner.Service]

  trait Service {

    val start: ZIO[Any, Nothing, Unit]

  }

  val start: ZIO[RegistrationCleaner, Nothing, Unit] = ZIO.accessM[RegistrationCleaner](_.get.start)

  val live: ZLayer[Logging with Clock with Has[RegistrationCleanupConfig] with UsersRepository with Has[doobie.Database.Service] with Has[
    SecurityConfig
  ] with KamonTracing, Nothing, Has[RegistrationCleaner.Service]] =
    ZLayer.fromServices[Logger[
      String
    ], Clock.Service, RegistrationCleanupConfig, UsersRepository.Service, Database.Service, SecurityConfig, KamonTracing.Service, RegistrationCleaner.Service] {
      (logger, clock, cleanupConfig, usersRepo, db, securityConfig, tracing) =>
        new Service {

          private val cleanup = tracing.createSpan(
            "Registrations cleaner",
            db
              .transactionOrDie(for {
                now                   <- clock.instant
                outdatedRegistrations <-
                  usersRepo.getUsersNotConfirmedBefore(now.minus(securityConfig.registrationValidFor.toNanos, ChronoUnit.NANOS))
                formattedIds = outdatedRegistrations.map(_.id.show).mkString(", ")
                _                     <- logger.info(show"Outdated registrations will be removed: $formattedIds").unless(outdatedRegistrations.isEmpty)
                _                     <- usersRepo.deleteUsersByIds(outdatedRegistrations.map(_.id): _*).unless(outdatedRegistrations.isEmpty)
              } yield ())
              .resurrect
          )

          override val start: ZIO[Any, Nothing, Unit] =
            cleanup
              .catchAll(err => logger.throwable("Registration cleanup failed", err))
              .repeat(Schedule.forever && Schedule.windowed(cleanupConfig.runEvery))
              .provideLayer(ZLayer.succeed(clock))
              .unit
        }
    }

}
