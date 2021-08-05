package com.github.huronapp.api.domain.users

import cats.data.NonEmptyList
import com.github.huronapp.api.auth.authentication.AuthenticationInputs
import com.github.huronapp.api.auth.authentication.TapirAuthenticationInputs.authRequestParts
import com.github.huronapp.api.domain.users.dto.fields.{Nickname, Password, PrivateKey, PublicKey, KeyPair => KeyPairDto}
import com.github.huronapp.api.domain.users.dto.{
  ApiKeyDataResp,
  GeneratePasswordResetReq,
  LoginReq,
  NewPersonalApiKeyReq,
  NewUserReq,
  PasswordResetReq,
  PatchUserDataReq,
  UpdateApiKeyDataReq,
  UpdatePasswordReq,
  UserDataResp,
  fields
}
import com.github.huronapp.api.http.{BaseEndpoint, ErrorResponse}
import com.github.huronapp.api.utils.Implicits.fuuid._
import io.chrisdavenport.fuuid.FUUID
import sttp.model.StatusCode
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir.EndpointIO.Example
import sttp.tapir.{Endpoint, oneOfMapping}
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._

object UsersEndpoints extends BaseEndpoint {

  private val usersEndpoint: ZEndpoint[Unit, Unit, Unit] = apiEndpoint.tag("users").in("users")

  private val exampleKeypairInput = KeyPairDto(KeyAlgorithm.Rsa, PublicKey("rsa-public-key..."), PrivateKey("encrypted-rsa-private-key..."))

  val registerUserEndpoint: Endpoint[NewUserReq, ErrorResponse, Unit, Any] = usersEndpoint
    .summary("Register new user")
    .post
    .in(
      jsonBody[NewUserReq].example(
        NewUserReq(Nickname("Alice"), Email("foo@example.org"), Password("secret-password"), Some(Language.En), exampleKeypairInput)
      )
    )
    .out(statusCode(StatusCode.Created))
    .errorOut(
      oneOf[ErrorResponse](
        badRequest,
        oneOfMapping(StatusCode.Conflict, jsonBody[ErrorResponse.Conflict].description("Email already registered"))
      )
    )

  val confirmRegistrationEndpoint: Endpoint[String, ErrorResponse, Unit, Any] = usersEndpoint
    .summary("Signup confirmation")
    .get
    .in("registrations" / path[String]("confirmationToken"))
    .out(statusCode(StatusCode.NoContent))
    .errorOut(oneOf[ErrorResponse](oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Invalid token"))))

  val loginEndpoint: Endpoint[LoginReq, ErrorResponse, (UserDataResp, CookieValueWithMeta, String), Any] = usersEndpoint
    .summary("User login")
    .post
    .in("auth" / "login")
    .in(jsonBody[LoginReq])
    .out(jsonBody[UserDataResp])
    .out(setCookie("session").description("Cookie with session id"))
    .out(header[String]("X-Csrf-Token"))
    .errorOut(
      oneOf[ErrorResponse](
        badRequest,
        oneOfMapping(StatusCode.Unauthorized, jsonBody[ErrorResponse.Unauthorized].description("Invalid credentials"))
      )
    )

  val logoutEndpoint: Endpoint[AuthenticationInputs, ErrorResponse, CookieValueWithMeta, Any] = usersEndpoint
    .summary("Logout user")
    .delete
    .prependIn(authRequestParts)
    .in("me" / "session")
    .out(statusCode(StatusCode.NoContent))
    .out(setCookie("session").description("Expired cookie with current session ID"))
    .errorOut(
      oneOf[ErrorResponse](
        unauthorized,
        oneOfMapping(StatusCode.Forbidden, jsonBody[ErrorResponse.Forbidden].description("Logout only allowed for cookie authentication"))
      )
    )

  val userDataEndpoint: Endpoint[AuthenticationInputs, ErrorResponse, (UserDataResp, Option[FUUID]), Any] = usersEndpoint
    .summary("Current users data")
    .get
    .prependIn(authRequestParts)
    .in("me" / "data")
    .out(jsonBody[UserDataResp])
    .out(header[Option[FUUID]]("X-Csrf-Token"))
    .errorOut(
      oneOf[ErrorResponse](
        unauthorized,
        oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("User data not found"))
      )
    )

  val updateUserDataEndpoint: Endpoint[(AuthenticationInputs, PatchUserDataReq), ErrorResponse, UserDataResp, Any] = usersEndpoint
    .summary("Update user")
    .patch
    .prependIn(authRequestParts)
    .in("me" / "data")
    .in(jsonBody[PatchUserDataReq])
    .out(jsonBody[UserDataResp])
    .errorOut(
      oneOf[ErrorResponse](
        unauthorized,
        oneOfMapping(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description("No updates provided")),
        oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("User not found"))
      )
    )

  private val updatePasswordPasswordsEqualsExample = Example(
    Responses.updatePasswordPasswordsEquals,
    Responses.updatePasswordPasswordsEquals.reason,
    Responses.updatePasswordPasswordsEquals.reason
  )

  private val updatePasswordInvalidCredentials = Example(
    Responses.updatePasswordInvalidCredentials,
    Responses.updatePasswordInvalidCredentials.reason,
    Responses.updatePasswordInvalidCredentials.reason
  )

  private val updatePasswordInvalidEmail = Example(
    Responses.updatePasswordInvalidEmail,
    Responses.updatePasswordInvalidEmail.reason,
    Responses.updatePasswordInvalidEmail.reason
  )

  private val updatePasswordMissingKeys = Example(
    Responses.updatePasswordMissingEncryptionKeys(
      Set(FUUID.fuuid("8ad2e9e8-1eb3-4c7c-b4c3-c14626ba4c73"), FUUID.fuuid("b41f8838-4903-405c-82f4-d5d157bccf19"))
    ),
    Responses.updatePasswordMissingEncryptionKeys(Set.empty).reason,
    Responses.updatePasswordMissingEncryptionKeys(Set.empty).reason
  )

  private val updatePasswordKeyVersionMismatch = {
    val exampleResponse = Responses.updatePasswordKeyVersionMismatch(
      FUUID.fuuid("8ad2e9e8-1eb3-4c7c-b4c3-c14626ba4c73"),
      FUUID.fuuid("b41f8838-4903-405c-82f4-d5d157bccf19")
    )

    Example(exampleResponse, exampleResponse.reason, exampleResponse.reason)
  }

  val updateUserPasswordEndpoint: Endpoint[(AuthenticationInputs, UpdatePasswordReq), ErrorResponse, Unit, Any] = usersEndpoint
    .summary("Change password")
    .post
    .prependIn(authRequestParts)
    .in("me" / "password")
    .in(jsonBody[UpdatePasswordReq])
    .out(statusCode(StatusCode.NoContent))
    .errorOut(
      oneOf[ErrorResponse](
        unauthorized,
        badRequest,
        oneOfMapping(StatusCode.Forbidden, jsonBody[ErrorResponse.Forbidden]),
        oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("User not found")),
        oneOfMapping(
          StatusCode.PreconditionFailed,
          jsonBody[ErrorResponse.PreconditionFailed]
            .examples(
              List(
                updatePasswordPasswordsEqualsExample,
                updatePasswordInvalidCredentials,
                updatePasswordInvalidEmail,
                updatePasswordMissingKeys,
                updatePasswordKeyVersionMismatch
              )
            )
        )
      )
    )

  val requestPasswordResetEndpoint: Endpoint[GeneratePasswordResetReq, ErrorResponse, Unit, Any] = usersEndpoint
    .summary("Request password reset")
    .post
    .in("password")
    .in(jsonBody[GeneratePasswordResetReq])
    .out(statusCode(StatusCode.NoContent))
    .errorOut(oneOf[ErrorResponse](badRequest))

  val passwordResetEndpoint: Endpoint[(String, PasswordResetReq), ErrorResponse, Unit, Any] = usersEndpoint
    .summary("Reset password using token")
    .put
    .in("password" / path[String]("passwordResetToken"))
    .in(jsonBody[PasswordResetReq])
    .out(statusCode(StatusCode.NoContent))
    .errorOut(
      oneOf[ErrorResponse](badRequest, oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Token not found")))
    )

  object Responses {

    val updatePasswordPasswordsEquals: ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed("New and old passwords are equal", Some("PasswordsEqual"))

    val updatePasswordInvalidCredentials: ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed("Current password is incorrect", Some("InvalidCurrentPassword"))

    val updatePasswordInvalidEmail: ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed("Email is incorrect", Some("InvalidEmail"))

    def updatePasswordMissingEncryptionKeys(collectionId: Set[FUUID]): ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed(
        s"Missing encryption key for collections: ${collectionId.mkString(", ")}",
        Some("MissingEncryptionKeys")
      )

    def updatePasswordKeyVersionMismatch(collectionId: FUUID, currentVersion: FUUID): ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed(
        s"Key version for collection $collectionId should be $currentVersion",
        Some("KeyVersionMismatch")
      )

  }

  val createPersonalApiKeyEndpoint: Endpoint[(AuthenticationInputs, NewPersonalApiKeyReq), ErrorResponse, ApiKeyDataResp, Any] =
    usersEndpoint
      .summary("Create personal API key")
      .post
      .prependIn(authRequestParts)
      .in("me" / "api-keys")
      .in(jsonBody[NewPersonalApiKeyReq])
      .out(jsonBody[ApiKeyDataResp])
      .errorOut(oneOf[ErrorResponse](badRequest, unauthorized))

  val listPersonalApiKeysEndpoint: Endpoint[AuthenticationInputs, ErrorResponse, List[ApiKeyDataResp], Any] = usersEndpoint
    .summary("List users API keys")
    .get
    .prependIn(authRequestParts)
    .in("me" / "api-keys")
    .out(jsonBody[List[ApiKeyDataResp]])
    .errorOut(oneOf[ErrorResponse](unauthorized))

  val deleteApiKeyEndpoint: Endpoint[(AuthenticationInputs, FUUID), ErrorResponse, Unit, Any] =
    usersEndpoint
      .summary("Delete personal API key")
      .delete
      .prependIn(authRequestParts)
      .in("me" / "api-keys" / path[FUUID]("apiKeyId"))
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("API token not found"))
        )
      )

  val updateApiKeyEndpoint: Endpoint[(AuthenticationInputs, FUUID, UpdateApiKeyDataReq), ErrorResponse, ApiKeyDataResp, Any] = usersEndpoint
    .summary("Update personal API key")
    .patch
    .prependIn(authRequestParts)
    .in("me" / "api-keys" / path[FUUID]("apiKeyId"))
    .in(jsonBody[UpdateApiKeyDataReq])
    .out(jsonBody[ApiKeyDataResp])
    .errorOut(
      oneOf[ErrorResponse](
        unauthorized,
        oneOfMapping(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description("No updates provided")),
        oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("API key not found"))
      )
    )

  val userKeypairEndpoint: Endpoint[AuthenticationInputs, ErrorResponse, fields.KeyPair, Any] = usersEndpoint
    .summary("Current users key pair")
    .get
    .prependIn(authRequestParts)
    .in("me" / "keypair")
    .out(jsonBody[KeyPairDto])
    .errorOut(
      oneOf[ErrorResponse](
        unauthorized,
        oneOfMapping(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Keypair not found for user"))
      )
    )

  val endpoints: NonEmptyList[ZEndpoint[_, _, _]] =
    NonEmptyList.of(
      registerUserEndpoint,
      confirmRegistrationEndpoint,
      loginEndpoint,
      logoutEndpoint,
      userDataEndpoint,
      updateUserDataEndpoint,
      updateUserPasswordEndpoint,
      requestPasswordResetEndpoint,
      passwordResetEndpoint,
      createPersonalApiKeyEndpoint,
      listPersonalApiKeysEndpoint,
      deleteApiKeyEndpoint,
      updateApiKeyEndpoint,
      userKeypairEndpoint
    )

}
