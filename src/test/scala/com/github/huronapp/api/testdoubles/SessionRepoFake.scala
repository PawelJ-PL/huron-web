package com.github.huronapp.api.testdoubles

import cats.syntax.eq._
import com.github.huronapp.api.authentication.SessionRepository
import com.github.huronapp.api.domain.users.UserSession
import com.github.huronapp.api.utils.RandomUtils
import com.github.huronapp.api.utils.RandomUtils.RandomUtils
import io.chrisdavenport.fuuid.FUUID
import zio.clock.Clock
import zio.{Has, Ref, Task, ZLayer}

object SessionRepoFake {

  final case class SessionRepoState(sessions: Set[UserSession] = Set.empty)

  def create(ref: Ref[SessionRepoState]): ZLayer[RandomUtils with Clock, Nothing, Has[SessionRepository.Service]] =
    ZLayer.fromServices[RandomUtils.Service, Clock.Service, SessionRepository.Service]((random, clock) =>
      new SessionRepository.Service {

        override def sessionCreate(userId: FUUID): Task[UserSession] =
          for {
            sessionId <- random.randomFuuid
            csrfToken <- random.randomFuuid
            now       <- clock.instant
            session = UserSession(sessionId, userId, csrfToken, now)
            _         <- ref.update(prev => prev.copy(sessions = prev.sessions + session))
          } yield session

        override def getSession(sessionId: FUUID): Task[Option[UserSession]] = ref.get.map(_.sessions.find(_.sessionId === sessionId))

        override def deleteSession(sessionId: FUUID): Task[Unit] =
          ref.update(prev => prev.copy(sessions = prev.sessions.filter(_.sessionId =!= sessionId)))

        override def deleteAllUsersSessions(userId: FUUID): Task[Int] = ???

      }
    )

}
