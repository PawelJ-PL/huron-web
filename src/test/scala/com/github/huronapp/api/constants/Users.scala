package com.github.huronapp.api.constants

import com.github.huronapp.api.domain.users.{ApiKey, ApiKeyType, Email, Language, User}
import io.chrisdavenport.fuuid.FUUID

import java.time.Instant

trait Users {

  final val ExampleUserId = FUUID.fuuid("431e092f-50ce-47eb-afbd-b806514d3f2c")

  final val ExampleUserNickName = "Alice"

  final val ExampleUserEmail = Email("alice@example.org")

  final val ExampleUserEmailDigest = "digest(alice@example.org)"

  final val ExampleUserPassword = "secret"

  final val ExampleUserLanguage = Language.Pl

  final val ExampleUserPasswordHash = "bcrypt(secret)"

  final val ExampleUser = User(ExampleUserId, ExampleUserEmailDigest, ExampleUserNickName, ExampleUserLanguage)

  final val ExampleApiKeyId = FUUID.fuuid("b0edc95b-5bf8-4be1-b272-e91dd391beee")

  final val ExampleApiKey =
    ApiKey(
      ExampleApiKeyId,
      "ABCD",
      ExampleUserId,
      ApiKeyType.Personal,
      "My Key",
      enabled = true,
      None,
      Instant.EPOCH,
      Instant.EPOCH
    )

}
