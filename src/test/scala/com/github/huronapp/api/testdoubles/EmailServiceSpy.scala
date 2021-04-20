package com.github.huronapp.api.testdoubles

import cats.data.Chain
import com.github.huronapp.api.domain.users.Email
import com.github.huronapp.api.utils.EmailService.EmailService
import zio.{Ref, ULayer, ZLayer}

object EmailServiceSpy {

  final case class SavedEmail(to: Email, subject: String, content: String)

  def create(emails: Ref[Chain[SavedEmail]]): ULayer[EmailService] =
    ZLayer.succeed((sendTo: Email, subject: String, content: String) => emails.update(_.append(SavedEmail(sendTo, subject, content))))

}
