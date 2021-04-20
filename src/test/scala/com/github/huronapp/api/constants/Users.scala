package com.github.huronapp.api.constants

import com.github.huronapp.api.domain.users.{Email, Language, User}
import io.chrisdavenport.fuuid.FUUID

trait Users {

  final val ExampleUserId = FUUID.fuuid("431e092f-50ce-47eb-afbd-b806514d3f2c")

  final val ExampleUserNickName = "Alice"

  final val ExampleUserEmail = Email("alice@example.org")

  final val ExampleUserEmailDigest = "digest(alice@example.org)"

  final val ExampleUserPassword = "secret"

  final val ExampleUserLanguage = Language.Pl

  final val ExampleUserPasswordHash = "bcrypt(secret)"

  final val ExampleUser = User(ExampleUserId, ExampleUserEmailDigest, ExampleUserNickName, ExampleUserLanguage)

}
