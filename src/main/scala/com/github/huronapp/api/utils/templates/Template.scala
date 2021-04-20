package com.github.huronapp.api.utils.templates

import org.http4s.Uri
import yamusca.converter.ValueConverter
import yamusca.imports.Value

sealed trait Template {

  val templateName: String

}

object Template {

  final case class NewRegistration(nickName: String, token: String, tokenVerificationUri: Uri) extends Template {

    override val templateName: String = "NewRegistration"

  }

  object NewRegistration {

    // ValueConverter.deriveConverter[NewRegistration]  throws " not found: value Context"
    implicit val valueConverter: ValueConverter[NewRegistration] =
      ValueConverter.of[NewRegistration](t =>
        Value.fromMap(
          Map(
            "nickName" -> Value.of(t.nickName),
            "token" -> Value.of(t.token),
            "tokenVerificationUri" -> Value.of(t.tokenVerificationUri.renderString)
          )
        )
      )

  }

  final case class PasswordReset(nickName: String, token: String, passwordResetUri: Uri) extends Template {

    override val templateName: String = "PasswordReset"

  }

  object PasswordReset {

    // ValueConverter.deriveConverter[NewRegistration]  throws " not found: value Context"
    implicit val valueConverter: ValueConverter[PasswordReset] =
      ValueConverter.of[PasswordReset](t =>
        Value.fromMap(
          Map(
            "nickName" -> Value.of(t.nickName),
            "token" -> Value.of(t.token),
            "passwordResetUri" -> Value.of(t.passwordResetUri.renderString)
          )
        )
      )

  }

  implicit val valueConverter: ValueConverter[Template] = ValueConverter.of {
    case t: NewRegistration =>
      val converter = implicitly[ValueConverter[NewRegistration]]
      converter(t)
    case t: PasswordReset   =>
      val converter = implicitly[ValueConverter[PasswordReset]]
      converter(t)
  }

}
