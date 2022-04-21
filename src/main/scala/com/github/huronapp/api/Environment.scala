package com.github.huronapp.api

import cats.syntax.semigroupk._
import com.github.huronapp.api.auth.authentication.{HttpAuthentication, SessionCache, SessionRepository, SessionsIndex}
import com.github.huronapp.api.config.{AppConfig, SecurityConfig}
import com.github.huronapp.api.config.modules.{FileSystemConfig, SessionRepoConfig}
import com.github.huronapp.api.database.{DataSources, Database}
import com.github.huronapp.api.database.Database.Database
import com.github.huronapp.api.domain.devices.{DevicesEndpoints, DevicesRoutes}
import com.github.huronapp.api.domain.devices.DevicesRoutes.DevicesRoutes
import com.github.huronapp.api.auth.authentication.SessionsIndex.SessionsIndex
import com.github.huronapp.api.auth.authorization.AuthorizationKernel
import com.github.huronapp.api.domain.collections.CollectionsRoutes.CollectionsRoutes
import com.github.huronapp.api.domain.collections.{CollectionsEndpoints, CollectionsRepository, CollectionsRoutes, CollectionsService}
import com.github.huronapp.api.domain.files.{FilesEndpoints, FilesMetadataRepository, FilesRoutes, FilesService}
import com.github.huronapp.api.domain.files.FilesRoutes.FilesRoutes
import com.github.huronapp.api.domain.users.UsersRoutes.UserRoutes
import com.github.huronapp.api.domain.users.{UserSession, UsersEndpoints, UsersRepository, UsersRoutes, UsersService}
import com.github.huronapp.api.http.ApiDocRoutes
import com.github.huronapp.api.http.ApiDocRoutes.ApiDocRoutes
import com.github.huronapp.api.http.web.StaticRoutes
import com.github.huronapp.api.http.web.StaticRoutes.StaticRoutes
import com.github.huronapp.api.messagebus.{InternalMessage, InternalMessageBus}
import com.github.huronapp.api.scheduler.OutboxTasksDispatcher.OutboxTasksDispatcher
import com.github.huronapp.api.scheduler.{OutboxTasksDispatcher, RegistrationCleaner}
import com.github.huronapp.api.scheduler.RegistrationCleaner.RegistrationCleaner
import com.github.huronapp.api.utils.EmailService.EmailService
import com.github.huronapp.api.utils.FileSystemService.FileSystemService
import com.github.huronapp.api.utils.{EmailService, FileSystemService, RandomUtils}
import com.github.huronapp.api.utils.crypto.Crypto
import com.github.huronapp.api.utils.outbox.OutboxService.OutboxService
import com.github.huronapp.api.utils.outbox.{OutboxRepository, OutboxService, OutboxTaskProcessingQueue}
import com.github.huronapp.api.utils.templates.TemplateService
import com.github.huronapp.api.utils.templates.TemplateService.TemplateService
import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import io.github.gaelrenoux.tranzactio.doobie.{Database => DoobieDb}
import io.github.gaelrenoux.tranzactio.ErrorStrategies
import izumi.reflect.Tag
import scalacache.Cache
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{Has, Hub, Queue, Task, ULayer, ZEnv, ZLayer}
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import com.github.huronapp.api.utils.outbox.{Task => OutboxTask}
import io.chrisdavenport.fuuid.FUUID

import java.time.Duration

object Environment {

  private implicit class ConfigLayerOps(configLayer: ULayer[Has[AppConfig]]) {

    def narrow[A: Tag](extract: AppConfig => A): ZLayer[Any, Nothing, Has[A]] = configLayer.map(c => Has(extract(c.get)))

  }

  type AppEnvironment = Has[AppConfig]
    with DevicesRoutes
    with ApiDocRoutes
    with Database
    with UserRoutes
    with Has[Hub[InternalMessage]]
    with EmailService
    with Logging
    with TemplateService
    with RegistrationCleaner
    with KamonTracing
    with CollectionsRoutes
    with FilesRoutes
    with OutboxTasksDispatcher
    with Has[Queue[OutboxTask]]
    with OutboxService
    with FileSystemService
    with StaticRoutes

  def live(config: AppConfig): ZLayer[ZEnv, Throwable, AppEnvironment] = {
    val configLayer = ZLayer.succeed(config)
    val logging = Slf4jLogger.make((_, str) => str)
    val tracing = KamonTracing.live
    val internalMessageBus: ZLayer[Any, Nothing, Has[Hub[InternalMessage]]] = InternalMessageBus.live
    val devicesRoutes = configLayer.narrow(_.mobileApp) >>> DevicesRoutes.live
    val swaggerRoutes = ApiDocRoutes.live(
      DevicesEndpoints.endpoints <+> UsersEndpoints.endpoints <+> CollectionsEndpoints.endpoints <+> FilesEndpoints.endpoints
    )
    val database = configLayer.narrow(_.database) ++ Blocking.any ++ tracing >>> Database.live
    val random = Blocking.any ++ tracing ++ Clock.any >>> RandomUtils.live
    val crypto = random ++ configLayer.narrow(_.security) ++ tracing >>> Crypto.live
    val dataSource = Blocking.any ++ configLayer.narrow(_.database) >>> DataSources.hikari
    val errorStrategy = ZLayer.succeed(
      ErrorStrategies.timeout(Duration.ofSeconds(10)).retryForeverFixed(Duration.ofSeconds(10)).timeout(Duration.ofSeconds(15))
    )
    val db = dataSource ++ errorStrategy ++ Blocking.any ++ Clock.any >>> DoobieDb.fromDatasourceAndErrorStrategies
    val usersRepo = UsersRepository.postgres
    val securityConfig = configLayer.narrow(_.security)
    val collectionsRepo = CollectionsRepository.postgres
    val authKernel = db ++ collectionsRepo >>> AuthorizationKernel.live
    val usersService =
      crypto ++ usersRepo ++ db ++ random ++ logging ++ internalMessageBus ++ securityConfig ++ tracing ++ collectionsRepo ++ authKernel >>> UsersService.live
    val sessionsIndex = conditionalSessionIndex(config.security.sessionRepo)
    val sessionCache = securityConfig ++ Blocking.any ++ Clock.any >>> conditionalSessionStorage(config.security.sessionRepo)
    val sessionRepo = sessionsIndex ++ Clock.any ++ random ++ securityConfig ++ sessionCache ++ logging >>> SessionRepository.live
    val httpAuth = sessionRepo ++ usersRepo ++ db ++ logging ++ Clock.any ++ securityConfig >>> HttpAuthentication.live
    val userRoutes = Blocking.any ++ usersService ++ logging ++ securityConfig ++ sessionRepo ++ httpAuth >>> UsersRoutes.live
    val emailService = EmailService.console
    val templateService = logging >>> TemplateService.live
    val registrationCleanupConfig = configLayer.narrow(_.schedulers.registrationCleanup)
    val registrationCleaner = {
      logging ++ Clock.any ++ registrationCleanupConfig ++ usersRepo ++ db ++ securityConfig ++ tracing >>> RegistrationCleaner.live
    }
    val filesRepo = Clock.any >>> FilesMetadataRepository.postgres
    val collectionsService = db ++ collectionsRepo ++ filesRepo ++ usersRepo ++ authKernel ++ random ++ logging >>> CollectionsService.live
    val collectionsRoutes = collectionsService ++ logging ++ httpAuth >>> CollectionsRoutes.live
    val fs = Blocking.any ++ tracing >>> conditionalFsService(config.filesystemConfig)
    val outboxRepo = Clock.any >>> OutboxRepository.postgres
    val outboxService = outboxRepo ++ random ++ tracing ++ db >>> OutboxService.live
    val filesService =
      db ++ filesRepo ++ authKernel ++ random ++ logging ++ crypto ++ fs ++ collectionsRepo ++ outboxService >>> FilesService.live
    val filesRoutes = filesService ++ logging ++ httpAuth >>> FilesRoutes.live
    val outboxConfig = configLayer.narrow(_.outboxTasksConfig)
    val outboxTasksQueue = outboxConfig >>> OutboxTaskProcessingQueue.live
    val outboxTasksDispatcher = {
      outboxTasksQueue ++ logging ++ outboxConfig ++ Clock.any ++ db ++ outboxRepo ++ tracing >>> OutboxTasksDispatcher.live
    }
    val staticRoutes = StaticRoutes.live
    configLayer ++ devicesRoutes ++ swaggerRoutes ++ database ++ userRoutes ++ internalMessageBus ++ emailService ++ logging ++ templateService ++ registrationCleaner ++ tracing ++ collectionsRoutes ++ filesRoutes ++ outboxTasksDispatcher ++ outboxTasksQueue ++ outboxService ++ fs ++ staticRoutes
  }

  private def conditionalSessionIndex(config: SessionRepoConfig): ZLayer[Any, Nothing, SessionsIndex] =
    config match {
      case SessionRepoConfig.InMemorySessionRepo      => SessionsIndex.inMemory
      case config: SessionRepoConfig.RedisSessionRepo => SessionsIndex.redis(config)
    }

  private def conditionalSessionStorage(
    config: SessionRepoConfig
  ): ZLayer[Has[SecurityConfig] with Blocking with Clock, Throwable, Has[Cache[Task, FUUID, UserSession]]] =
    config match {
      case SessionRepoConfig.InMemorySessionRepo      => SessionCache.inMemory
      case config: SessionRepoConfig.RedisSessionRepo => SessionCache.redis(config)
    }

  private def conditionalFsService(config: FileSystemConfig): ZLayer[Blocking with KamonTracing, Throwable, FileSystemService] =
    config match {
      case FileSystemConfig.LocalFsConfig(root, createMissingRoot) => FileSystemService.localFs(root, createMissingRoot)
    }

}
