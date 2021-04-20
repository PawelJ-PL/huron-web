package com.github.huronapp.api.utils.templates

import cats.syntax.eq._
import com.github.huronapp.api.domain.users.Language
import yamusca.converter.syntax._
import yamusca.parser.parse
import yamusca.expand.renderWithMissingKeys
import zio.logging.Logger
import zio.{Has, ZIO, ZLayer, ZManaged}

import java.io.FileNotFoundException
import scala.io.Source

object TemplateService {

  type TemplateService = Has[TemplateService.Service]

  trait Service {

    def render(template: Template, language: Language): ZIO[Any, TemplateError, String]

  }

  val live: ZLayer[Has[Logger[String]], Nothing, TemplateService] = ZLayer.fromService[Logger[String], TemplateService.Service](logger =>
    new Service {

      private final val DefaultLanguage = "en"

      override def render(template: Template, language: Language): ZIO[Any, TemplateError, String] =
        loadTemplate(template.templateName, language.entryName.toLowerCase)
          .catchSome {
            case TemplateError.TemplateNotFound(path) if language.entryName.toLowerCase =!= DefaultLanguage =>
              logger.warn(s"Template $path not found, trying default one") *> loadTemplate(template.templateName, DefaultLanguage)
          }
          .flatMap(templateString => renderTemplate(templateString, template))

      private def loadTemplate(templateName: String, language: String): ZIO[Any, TemplateError, String] = {
        val templatesDir = "templates"
        val templatesExtension = ".mustache"
        val templatePath = templatesDir + "/" + language + "/" + templateName + templatesExtension
        ZManaged.fromAutoCloseable(ZIO(Source.fromResource(templatePath))).use(source => ZIO.succeed(source.mkString)).mapError {
          case _: FileNotFoundException => TemplateError.TemplateNotFound(templatePath)
          case err                      => TemplateError.TemplateLoadingFailed(templatePath, err)
        }
      }

      private def renderTemplate(templateString: String, templateData: Template): ZIO[Any, TemplateError, String] =
        ZIO
          .fromEither(parse(templateString))
          .mapError { case (_, error) => TemplateError.TemplateParseFailed(error) }
          .flatMap(parsedTemplate =>
            ZIO.effect(renderWithMissingKeys(parsedTemplate)(templateData.asContext)).mapError(err => TemplateError.RenderFailed(err))
          )
          .flatMap {
            case (missingValues, _, rendered) =>
              if (missingValues.isEmpty) ZIO.succeed(rendered) else ZIO.fail(TemplateError.TemplateMissingValue(missingValues))
          }

    }
  )

}

sealed trait TemplateError

object TemplateError {

  final case class TemplateNotFound(path: String) extends TemplateError

  final case class TemplateLoadingFailed(path: String, error: Throwable) extends TemplateError

  final case class TemplateParseFailed(error: String) extends TemplateError

  final case class TemplateMissingValue(missingValues: List[String]) extends TemplateError

  final case class RenderFailed(error: Throwable) extends TemplateError

}
