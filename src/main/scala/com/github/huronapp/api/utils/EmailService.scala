package com.github.huronapp.api.utils

import com.github.huronapp.api.domain.users.Email
import zio.console.Console
import zio.{Has, IO, Task, ZLayer}

import java.io.IOException

object EmailService {

  type EmailService = Has[EmailService.Service]

  trait Service {

    def send(sendTo: Email, subject: String, content: String): Task[Unit]

  }

  val console: ZLayer[Console, Nothing, EmailService] = ZLayer.fromService[Console.Service, EmailService.Service] { console =>
    new Service {
      private def printRepeated(content: String, n: Int): IO[IOException, Unit] = console.putStrLn(List.fill(n)(content).mkString)

      override def send(sendTo: Email, subject: String, content: String): Task[Unit] =
        console.putStrLn("TO: " + sendTo.value) *>
          printRepeated("-", 30) *>
          console.putStrLn("Subject: " + subject) *>
          printRepeated("-", 30) *>
          console.putStrLn(content) *>
          printRepeated("=", 30)
    }
  }

}
