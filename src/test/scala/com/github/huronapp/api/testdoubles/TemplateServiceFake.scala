package com.github.huronapp.api.testdoubles

import cats.Show
import cats.syntax.show._
import com.github.huronapp.api.domain.users.Language
import com.github.huronapp.api.utils.templates.TemplateService.TemplateService
import com.github.huronapp.api.utils.templates.Template
import zio.{ULayer, ZIO, ZLayer}

object TemplateServiceFake {
  val create: ULayer[TemplateService] = ZLayer.succeed((template: Template, language: Language) => {
    implicit val templateShow: Show[Template] = Show.fromToString
    ZIO.succeed(show"$language ----- $template")
  })
}
