package com.github.huronapp.api.utils

import sttp.tapir.{ValidationError, Validator}

object TapirValidators {

  val nonEmptyString: Validator[String] =
    Validator
      .custom[String](str => if (str.trim.length < 1) List(ValidationError.Custom(str, "Value can't be empty")) else List.empty, None)

}
