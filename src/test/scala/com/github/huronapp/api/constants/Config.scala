package com.github.huronapp.api.constants

import ciris.Secret
import com.github.huronapp.api.config.modules.FileSystemConfig.LocalFsConfig
import com.github.huronapp.api.config.{
  AppConfig,
  DatabaseConfig,
  MobileAppConfig,
  OutboxTasksConfig,
  RegistrationCleanupConfig,
  SchedulersConfig,
  SecurityConfig,
  ServerConfig
}
import com.github.huronapp.api.config.modules.{FileSystemConfig, SessionRepoConfig}
import com.vdurmont.semver4j.Semver
import org.http4s.implicits._
import zio.nio.core.file.Path

import scala.concurrent.duration._

trait Config {

  final val ExampleServerConfig = ServerConfig("0.0.0.0", 8080)

  final val ExampleMobileAppConfig = MobileAppConfig(new Semver("1.0.0"))

  final val ExampleDatabaseConfig = DatabaseConfig("jdbc:postgresql://127.0.0.1:5432/testdb", "testuser", Secret("testpassword"), 3, 1000)

  final val ExampleSecurityConfig = SecurityConfig(
    15,
    uri"http://app:8080/registration",
    1.day,
    "$2y$04$FS/UrYKODtETYCrEhI81O.xYKH1sOzSHIQqeqEmxrFRuecFmGj7Eq",
    7.days,
    SessionRepoConfig.InMemorySessionRepo,
    uri"http://app:8080/reset-password",
    1.day
  )

  final val ExampleSchedulerConfig = SchedulersConfig(
    RegistrationCleanupConfig(java.time.Duration.ofMinutes(10))
  )

  final val ExampleFsConfig: FileSystemConfig = LocalFsConfig(Path("/tmp/foo"), createMissingRoot = false)

  final val ExampleOutboxConfig: OutboxTasksConfig = OutboxTasksConfig(16, java.time.Duration.ofMinutes(2), java.time.Duration.ofHours(2))

  final val ExampleAppConfig =
    AppConfig(
      ExampleServerConfig,
      ExampleMobileAppConfig,
      ExampleDatabaseConfig,
      ExampleSecurityConfig,
      ExampleSchedulerConfig,
      ExampleFsConfig,
      ExampleOutboxConfig
    )

}
