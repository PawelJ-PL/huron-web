package com.github.huronapp.api.messagebus.handlers

import com.github.huronapp.api.config.AppConfig
import com.github.huronapp.api.domain.users.{Email, Language}
import com.github.huronapp.api.messagebus.InternalMessage
import com.github.huronapp.api.utils.EmailService
import com.github.huronapp.api.utils.EmailService.EmailService
import com.github.huronapp.api.utils.templates.{Template, TemplateService}
import com.github.huronapp.api.utils.templates.TemplateService.TemplateService
import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import kamon.context.Context
import zio.{Has, Hub, ZIO, ZManaged}
import zio.logging.{Logging, log}
import zio.stream.{ZSink, ZStream}

object EmailHandler {

  val handle: ZManaged[Has[Hub[InternalMessage]] with EmailService with Logging with TemplateService with Has[
    AppConfig
  ] with KamonTracing, Nothing, Unit] =
    ZManaged.accessM[Has[Hub[InternalMessage]] with EmailService with Logging with TemplateService with Has[AppConfig] with KamonTracing] {
      env =>
        ZStream.fromHub(env.get[Hub[InternalMessage]]).run(ZSink.foreach(process))
    }

  def process(
    message: InternalMessage
  ): ZIO[Logging with Has[EmailService.Service] with TemplateService with Has[AppConfig] with KamonTracing, Nothing, Unit] =
    ZIO
      .accessM[EmailService with TemplateService with Has[AppConfig] with KamonTracing] { env =>
        val (address, subject, template, language, context) = messageParams(message, env.get[AppConfig])
        val tracing = env.get[KamonTracing.Service]
        context
          .map(ctx =>
            tracing.withContext(
              tracing.createSpan(
                "Send email",
                sendEmail(address, subject, template, language),
                Map("mail.type" -> template.templateName, "mail.language" -> language.entryName)
              ),
              ctx
            )
          )
          .getOrElse(sendEmail(address, subject, template, language))
      }
      .resurrect
      .catchAll(err => log.throwable("Unable to send email", err))

  private def messageParams(message: InternalMessage, appConfig: AppConfig): (Email, String, Template, Language, Option[Context]) =
    message match {
      case InternalMessage.UserRegistered(user, email, registrationToken, tracingContext)          =>
        (
          email,
          "Huron App - account registration confirmation",
          Template.NewRegistration(
            user.nickName.capitalize,
            registrationToken,
            appConfig.security.registrationVerificationUri
          ),
          user.language,
          tracingContext
        )
      case InternalMessage.PasswordResetRequested(user, email, passwordResetToken, tracingContext) =>
        (
          email,
          "Huron App - password reset",
          Template.PasswordReset(user.nickName, passwordResetToken, appConfig.security.passwordResetUri),
          user.language,
          tracingContext
        )
    }

  private def sendEmail(
    address: Email,
    subject: String,
    template: Template,
    language: Language
  ): ZIO[EmailService with TemplateService with Has[AppConfig], Throwable, Unit] =
    ZIO
      .accessM[EmailService with TemplateService with Has[AppConfig]](env =>
        env
          .get[TemplateService.Service]
          .render(template, language)
          .mapError(templateError => new RuntimeException(templateError.toString))
          .flatMap(template => env.get[EmailService.Service].send(address, subject, template))
      )

}
