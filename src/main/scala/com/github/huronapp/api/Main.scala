package com.github.huronapp.api

import com.github.huronapp.api.config.AppConfig
import com.github.huronapp.api.database.Database
import com.github.huronapp.api.messagebus.handlers.{EmailHandler, OutboxTasksHandler}
import com.github.huronapp.api.scheduler.{OutboxTasksDispatcher, RegistrationCleaner}
import com.github.huronapp.api.utils.tracing.ZioKamonApp
import zio.{ExitCode, URIO, ZEnv, ZIO}
import zio.magic._

object Main extends ZioKamonApp {

  private def server(config: AppConfig): ZIO[ZEnv, Throwable, Nothing] =
    (for {
      _ <- Database.migrate.toManaged_
      emailHandler = EmailHandler.handle
      outboxHandler = OutboxTasksHandler.handle
      registrationCleanupScheduler = RegistrationCleaner.start.toManaged_
      outboxDispatcherScheduler = OutboxTasksDispatcher.start.toManaged_
      server = HttpServer.create
      _ <- emailHandler <&> outboxHandler <&> registrationCleanupScheduler <&> outboxDispatcherScheduler <&> server
    } yield ())
      .useForever
      .injectCustom(Environment.live(config))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = AppConfig.load.flatMap(server).exitCode

}
