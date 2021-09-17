package com.github.huronapp.api.config

import cats.syntax.parallel._
import ciris.{Secret, env}
import com.github.huronapp.api.config.instances._
import com.github.huronapp.api.config.modules.{FileSystemConfig, SessionRepoConfig}
import com.github.huronapp.api.utils.Implicits.semver._
import com.vdurmont.semver4j.Semver
import org.http4s.Uri
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{Has, Runtime, Task, ZIO}
import zio.interop.catz._

import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object AppConfig {

  type Config = Has[AppConfig]

  private val serverConfig = (
    env("HTTP_ADDRESS").as[String].default("0.0.0.0"),
    env("HTTP_PORT").or(env("PORT")).as[Int].default(8080)
  ).parMapN(ServerConfig)

  private val mobileAppConfig = env("MOBILE_APP_MIN_VERSION").as[Semver].map(MobileAppConfig)

  private val dbConfig = (
    env("DATABASE_URL").as[String],
    env("DATABASE_USER").as[String],
    env("DATABASE_PASSWORD").as[String].secret,
    env("DATABASE_MAX_POOL_SIZE").as[Int].or(ConfigSource.runtime(r => (r.availableProcessors() * 2) + 1).as[Int]),
    env("DATABASE_CONNECTION_TIMEOUT").as[Int].default(5000)
  ).parMapN(DatabaseConfig)

  private val securityConfig = (
    env("BCRYPT_ROUNDS").as[Int].default(15),
    env("REGISTRATION_VERIFICATION_URI").as[Uri],
    env("REGISTRATION_VALID_FOR").as[FiniteDuration],
    env("DUMMY_BCRYPT_HASH").as[String],
    env("SESSION_COOKIE_TTL").as[FiniteDuration].default(FiniteDuration(7, TimeUnit.DAYS)),
    SessionRepoConfig.load,
    env("PASSWORD_RESET_URI").as[Uri],
    env("PASSWORD_RESET_VALID_FOR").as[FiniteDuration]
  )
    .parMapN(SecurityConfig)

  private val registrationCleanupConfig = (
    env("REGISTRATION_CLEANUP_EVERY").as[JDuration].default(JDuration.ofMinutes(10))
  ).map(RegistrationCleanupConfig)

  private val outboxTasksConfig = (
    env("OUTBOX_TASK_QUEUE_SIZE").as[Int].default(16),
    env("OUTBOX_QUEUE_PROCESS_EVERY").as[JDuration].default(JDuration.ofMinutes(3)),
    env("OUTBOX_TASK_DURATION_CUTOFF").as[JDuration].default(JDuration.ofMinutes(15))
  ).parMapN(OutboxTasksConfig)

  private val appConfig =
    (serverConfig, mobileAppConfig, dbConfig, securityConfig, registrationCleanupConfig, FileSystemConfig.load, outboxTasksConfig)
      .parMapN((server, mobileApp, db, security, registrationCleanup, fsConfig, outbox) =>
        AppConfig(server, mobileApp, db, security, SchedulersConfig(registrationCleanup), fsConfig, outbox)
      )

  val load: ZIO[Blocking with Clock, Throwable, AppConfig] =
    ZIO.runtime[Clock with Blocking].flatMap { implicit r: Runtime[Clock with Blocking] => appConfig.load[Task] }

}

final case class AppConfig(
  server: ServerConfig,
  mobileApp: MobileAppConfig,
  database: DatabaseConfig,
  security: SecurityConfig,
  schedulers: SchedulersConfig,
  filesystemConfig: FileSystemConfig,
  outboxTasksConfig: OutboxTasksConfig)

final case class ServerConfig(bindAddress: String, port: Int)

final case class MobileAppConfig(lastSupportedVersion: Semver)

final case class DatabaseConfig(url: String, username: String, password: Secret[String], maxPoolSize: Int, connectionTimeout: Int)

final case class SecurityConfig(
  bcryptRounds: Int,
  registrationVerificationUri: Uri,
  registrationValidFor: FiniteDuration,
  dummyBcryptHash: String,
  sessionCookieTtl: FiniteDuration,
  sessionRepo: SessionRepoConfig,
  passwordResetUri: Uri,
  passwordResetValidFor: FiniteDuration)

final case class SchedulersConfig(registrationCleanup: RegistrationCleanupConfig)

final case class RegistrationCleanupConfig(runEvery: JDuration)

final case class OutboxTasksConfig(taskQueueSize: Int, runEvery: JDuration, taskDurationCutoff: JDuration)
