package com.github.huronapp.api.messagebus.handlers

import cats.data.Chain
import com.github.huronapp.api.constants.{Config, Users}
import com.github.huronapp.api.messagebus.InternalMessage
import com.github.huronapp.api.testdoubles.{EmailServiceSpy, KamonTracingFake, TemplateServiceFake}
import com.github.huronapp.api.testdoubles.EmailServiceSpy.SavedEmail
import kamon.context.Context
import zio.logging.slf4j.Slf4jLogger
import zio.{Ref, ZIO, ZLayer}
import zio.test.environment.TestEnvironment
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

object EmailHandlerSpec extends DefaultRunnableSpec with Config with Users {

  private val logger = Slf4jLogger.make((_, str) => str)

  private def env(emails: Ref[Chain[SavedEmail]]) =
    logger ++ EmailServiceSpy.create(emails) ++ TemplateServiceFake.create ++ ZLayer.succeed(ExampleAppConfig) ++ KamonTracingFake.noOp

  private def process(message: InternalMessage)(emails: Ref[Chain[SavedEmail]]): ZIO[Any, Nothing, Unit] =
    EmailHandler.process(message).provideLayer(env(emails))

  override def spec: ZSpec[TestEnvironment, Any] = suite("Email handler suite")(registeredUserEmail, passwordResetEmail)

  private val registeredUserEmail = testM("should send registration email") {
    val message = InternalMessage.UserRegistered(ExampleUser, ExampleUserEmail, "abc", Some(Context.Empty))

    for {
      emails     <- Ref.make[Chain[SavedEmail]](Chain.empty)
      _          <- process(message)(emails)
      sentEmails <- emails.get
    } yield assert(sentEmails)(
      equalTo(
        Chain.one(
          SavedEmail(
            ExampleUserEmail,
            "Huron App - account registration confirmation",
            "Pl ----- NewRegistration(Alice,abc,http://app:8080/registration)"
          )
        )
      )
    )
  }

  private val passwordResetEmail = testM("should send password reset email") {
    val message = InternalMessage.PasswordResetRequested(ExampleUser, ExampleUserEmail, "abc", Some(Context.Empty))

    for {
      emails     <- Ref.make[Chain[SavedEmail]](Chain.empty)
      _          <- process(message)(emails)
      sentEmails <- emails.get
    } yield assert(sentEmails)(
      equalTo(
        Chain.one(
          SavedEmail(
            ExampleUserEmail,
            "Huron App - password reset",
            "Pl ----- PasswordReset(Alice,abc,http://app:8080/reset-password)"
          )
        )
      )
    )
  }

}
