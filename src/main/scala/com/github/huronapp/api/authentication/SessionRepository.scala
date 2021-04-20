package com.github.huronapp.api.authentication

import cats.effect.{Clock => CEClock}
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
import zio.clock.Clock
import zio.logging.{Logger, Logging}
import zio.{Has, Task, ZIO, ZLayer}

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

  val inMemory: ZLayer[Any with Has[SecurityConfig], Throwable, Has[Cache[Task, UserSession]]] = {
    implicit val catsClock: CEClock[Task] = CEClock.create[Task]

    CaffeineCache[Task, UserSession].toLayer
    ZLayer.fromServiceM[SecurityConfig, Any, Throwable, Cache[Task, UserSession]] { securityConfig =>
      val ttl = java.time.Duration.ofNanos(securityConfig.sessionCookieTtl.toNanos)
      val underlyingCache: Task[cache.Cache[String, Entry[UserSession]]] =
        ZIO(Caffeine.newBuilder().expireAfterWrite(ttl).build[String, Entry[UserSession]])
      underlyingCache.map(c => CaffeineCache[Task, UserSession](c))
    }
  }

  def redis(config: RedisSessionRepo): ZLayer[Any, Throwable, Has[Cache[Task, UserSession]]] = {
    import scalacache.serialization.circe._

    ZIO.effect(RedisCache[Task, UserSession](config.host, config.port)).toLayer
  }

}
