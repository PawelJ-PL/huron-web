package com.github.huronapp.api.auth.authentication

import com.github.huronapp.api.config.SecurityConfig
import com.github.huronapp.api.config.modules.SessionRepoConfig.RedisSessionRepo
import SessionsIndex.SessionsIndex
import com.github.benmanes.caffeine.cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.huronapp.api.domain.users.UserSession
import com.github.huronapp.api.utils.RandomUtils
import com.github.huronapp.api.utils.RandomUtils.RandomUtils
import io.chrisdavenport.fuuid.FUUID
import scalacache._
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache
import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.{Logger, Logging}
import zio.{Has, Runtime, Task, ZIO, ZLayer}

object SessionRepository {

  type SessionRepository = Has[SessionRepository.Service]

  trait Service {

    def sessionCreate(userId: FUUID): Task[UserSession]

    def getSession(sessionId: FUUID): Task[Option[UserSession]]

    def deleteSession(sessionId: FUUID): Task[Unit]

    def deleteAllUsersSessions(userId: FUUID): Task[Int]

  }

  val live: ZLayer[SessionsIndex with Clock with RandomUtils with Has[
    Cache[Task, UserSession]
  ] with Logging, Nothing, Has[Service]] =
    ZLayer.fromServices[SessionsIndex.Service, Clock.Service, RandomUtils.Service, Cache[
      Task,
      UserSession
    ], Logger[String], SessionRepository.Service]((index, clock, random, sessionCache, logger) =>
      new Service {

        override def sessionCreate(userId: FUUID): Task[UserSession] =
          for {
            now       <- clock.instant
            sessionId <- random.randomFuuid
            csrfToken <- random.randomFuuid
            _         <- index.appendSession(userId, sessionId)
            userSession = UserSession(sessionId, userId, csrfToken, now)
            _         <- put(sessionId)(userSession)(sessionCache, Flags.defaultFlags)
          } yield userSession

        override def getSession(sessionId: FUUID): Task[Option[UserSession]] = get(sessionId)(sessionCache, Flags.defaultFlags)

        override def deleteSession(sessionId: FUUID): Task[Unit] =
          (for {
            existingSession <- get(sessionId)(sessionCache, Flags.defaultFlags).someOrFail(SessionNotFound)
            _               <- remove(sessionId)(sessionCache)
            _               <- index.deleteSessions(existingSession.userId, sessionId)
          } yield ())
            .catchSome {
              case SessionNotFound => logger.warn("Unable to remove session because it doesn't exists")
            }

        override def deleteAllUsersSessions(userId: FUUID): Task[Int] =
          for {
            sessions <- index.getAllByUser(userId)
            _        <- ZIO.foreach_(sessions)(session => remove(session)(sessionCache))
            _        <- index.deleteSessions(userId, sessions.toList: _*)
          } yield sessions.size

      }
    )

}

private case object SessionNotFound extends Throwable

object SessionCache {
  import zio.interop.catz._

  val inMemory: ZLayer[Blocking with Clock with Has[SecurityConfig], Throwable, Has[Cache[Task, UserSession]]] =
    ZLayer.fromServicesM[Blocking.Service, Clock.Service, SecurityConfig, Any, Throwable, Cache[Task, UserSession]] {
      (blocking, clock, securityConfig) =>
        val ttl = java.time.Duration.ofNanos(securityConfig.sessionCookieTtl.toNanos)
        val underlyingCache: Task[cache.Cache[String, Entry[UserSession]]] =
          ZIO(Caffeine.newBuilder().expireAfterWrite(ttl).build[String, Entry[UserSession]])
        ZIO
          .runtime[Clock with Blocking]
          .flatMap { implicit r: Runtime[Clock with Blocking] => underlyingCache.map(c => CaffeineCache[Task, UserSession](c)) }
          .provideLayer(ZLayer.succeed(blocking) ++ ZLayer.succeed(clock))
    }

  def redis(config: RedisSessionRepo): ZLayer[Blocking with Clock, Throwable, Has[Cache[Task, UserSession]]] = {
    import scalacache.serialization.circe._
    ZLayer.fromServicesM[Blocking.Service, Clock.Service, Any, Throwable, Cache[Task, UserSession]] { (blocking, clock) =>
      ZIO
        .runtime[Clock with Blocking]
        .flatMap { implicit r: Runtime[Clock with Blocking] =>
          ZIO.effect(RedisCache[Task, UserSession](config.host, config.port))
        }
        .provideLayer(ZLayer.succeed(blocking) ++ ZLayer.succeed(clock))
    }
  }

}
