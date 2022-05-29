package com.github.huronapp.api.utils

import sttp.tapir.{ValidationResult, Validator}

object TapirValidators {

  val nonEmptyString: Validator[String] =
    Validator
      .custom[String](str => if (str.trim.length < 1) ValidationResult.Invalid("Value can't be empty") else ValidationResult.Valid)

}
