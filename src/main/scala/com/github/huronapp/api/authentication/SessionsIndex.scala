package com.github.huronapp.api.authentication

import com.github.huronapp.api.config.modules.SessionRepoConfig.RedisSessionRepo
import io.chrisdavenport.fuuid.FUUID
import zio.{Has, Ref, ZIO, ZLayer}

object SessionsIndex {

  type SessionsIndex = Has[SessionsIndex.Service]

  trait Service {

    def appendSession(userId: FUUID, sessionId: FUUID): ZIO[Any, Nothing, Unit]

    def deleteSessions(userId: FUUID, sessionIds: FUUID*): ZIO[Any, Nothing, Unit]

    def getAllByUser(userId: FUUID): ZIO[Any, Nothing, Set[FUUID]]

  }

  val inMemory: ZLayer[Any, Nothing, SessionsIndex] = Ref
    .make[Map[FUUID, Set[FUUID]]](Map.empty)
    .map(ref =>
      new Service {

        override def appendSession(userId: FUUID, sessionId: FUUID): ZIO[Any, Nothing, Unit] =
          ref.update(_.updatedWith(userId) {
            case Some(sessionIds) => Some(sessionIds + sessionId)
            case None             => Some(Set(sessionId))
          })

        override def deleteSessions(userId: FUUID, sessionIds: FUUID*): ZIO[Any, Nothing, Unit] =
          ref.update { prev =>
            val updatedSessionsOfUser = prev.get(userId).map(_.filter(sessionId => !sessionIds.contains(sessionId)))
            updatedSessionsOfUser match {
              case Some(sessionIds) if sessionIds.isEmpty => prev - userId
              case Some(sessionIds)                       => prev.updated(userId, sessionIds)
              case None                                   => prev
            }
          }

        override def getAllByUser(userId: FUUID): ZIO[Any, Nothing, Set[FUUID]] = ref.get.map(_.getOrElse(userId, Set.empty))

      }
    )
    .toLayer

  def redis(config: RedisSessionRepo): ZLayer[Any, Nothing, SessionsIndex] = ZLayer.succeed(???)

}
