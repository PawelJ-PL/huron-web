package com.github.huronapp.api.domain.users

import cats.data.NonEmptyList
import com.github.huronapp.api.auth.authentication.AuthenticationInputs
import com.github.huronapp.api.auth.authentication.TapirAuthenticationInputs.authRequestParts
import com.github.huronapp.api.domain.users.dto.fields.{Nickname, Password, PrivateKey, PublicKey, KeyPair => KeyPairDto}
import com.github.huronapp.api.domain.users.dto.{
  ApiKeyDataResp,
  GeneratePasswordResetReq,
  LoginReq,
  NewContactReq,
  NewPersonalApiKeyReq,
  NewUserReq,
  PasswordResetReq,
  PatchContactReq,
  PatchUserDataReq,
  PublicUserContactResp,
  PublicUserDataResp,
  UpdateApiKeyDataReq,
  UpdatePasswordReq,
  UserContactResponse,
  UserDataResp
}
import com.github.huronapp.api.http.pagination.{Paging, PagingResponseMetadata}
import com.github.huronapp.api.http.{BaseEndpoint, ErrorResponse}
import com.github.huronapp.api.utils.Implicits.fuuid._
import com.github.huronapp.api.utils.Implicits.fuuidKeyMap._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.{MaxSize, MinSize}
import eu.timepit.refined.string.Trimmed
import io.chrisdavenport.fuuid.FUUID
import sttp.model.StatusCode
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir.EndpointIO.Example
import sttp.tapir.{Endpoint, PublicEndpoint}
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._
import sttp.tapir.codec.refined._

object UsersEndpoints extends BaseEndpoint {

  private val usersEndpoint: Endpoint[Unit, Unit, Unit, Unit, Any] = publicApiEndpoint.tag("users").in("users")

  object Responses {

    val emailConflict: ErrorResponse.Conflict = ErrorResponse.Conflict("Email already registered", Some("EmailAlreadyRegistered"))

    val nickNameConflict: ErrorResponse.Conflict = ErrorResponse.Conflict("Nickname already registered", Some("NickNameAlreadyRegistered"))

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

    val contactConflict: ErrorResponse.Conflict = ErrorResponse.Conflict("Contact already exists", Some("ContactAlreadyExists"))

    val contactAliasConflict: ErrorResponse.Conflict =
      ErrorResponse.Conflict("Contact alias already exists", Some("ContactAliasAlreadyExists"))

    val addSelfToContacts: ErrorResponse.PreconditionFailed =
      ErrorResponse.PreconditionFailed("Unable to add self to contacts", Some("AddSelfToContacts"))

  }

  private val exampleKeypairInput = KeyPairDto(KeyAlgorithm.Rsa, PublicKey("rsa-public-key..."), PrivateKey("encrypted-rsa-private-key..."))

  private val emailConflictExample = Example(Responses.emailConflict, Responses.emailConflict.reason, Responses.emailConflict.reason)

  private val nickNameConflictExample =
    Example(Responses.nickNameConflict, Responses.nickNameConflict.reason, Responses.nickNameConflict.reason)

  val registerUserEndpoint: PublicEndpoint[NewUserReq, ErrorResponse, Unit, Any] = usersEndpoint
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
        oneOfVariant(StatusCode.Conflict, jsonBody[ErrorResponse.Conflict].examples(List(emailConflictExample, nickNameConflictExample)))
      )
    )

  val confirmRegistrationEndpoint: PublicEndpoint[String, ErrorResponse, Unit, Any] = usersEndpoint
    .summary("Signup confirmation")
    .get
    .in("registrations" / path[String]("confirmationToken"))
    .out(statusCode(StatusCode.NoContent))
    .errorOut(oneOf[ErrorResponse](oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Invalid token"))))

  val loginEndpoint: PublicEndpoint[LoginReq, ErrorResponse, (UserDataResp, CookieValueWithMeta, String), Any] =
    usersEndpoint
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
          oneOfVariant(StatusCode.Unauthorized, jsonBody[ErrorResponse.Unauthorized].description("Invalid credentials"))
        )
      )

  val logoutEndpoint: Endpoint[AuthenticationInputs, Unit, ErrorResponse, CookieValueWithMeta, Any] =
    usersEndpoint
      .summary("Logout user")
      .delete
      .securityIn(authRequestParts)
      .in("me" / "session")
      .out(statusCode(StatusCode.NoContent))
      .out(setCookie("session").description("Expired cookie with current session ID"))
      .errorOut(
        oneOf[ErrorResponse](
          unauthorized,
          oneOfVariant(StatusCode.Forbidden, jsonBody[ErrorResponse.Forbidden].description("Logout only allowed for cookie authentication"))
        )
      )

  val userDataEndpoint: Endpoint[AuthenticationInputs, Unit, ErrorResponse, (UserDataResp, Option[FUUID]), Any] =
    usersEndpoint
      .summary("Current users data")
      .get
      .securityIn(authRequestParts)
      .in("me" / "data")
      .out(jsonBody[UserDataResp])
      .out(header[Option[FUUID]]("X-Csrf-Token"))
      .errorOut(
        oneOf[ErrorResponse](
          unauthorized,
          oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("User data not found"))
        )
      )

  val updateUserDataEndpoint: Endpoint[AuthenticationInputs, PatchUserDataReq, ErrorResponse, UserDataResp, Any] =
    usersEndpoint
      .summary("Update user")
      .patch
      .securityIn(authRequestParts)
      .in("me" / "data")
      .in(jsonBody[PatchUserDataReq])
      .out(jsonBody[UserDataResp])
      .errorOut(
        oneOf[ErrorResponse](
          unauthorized,
          oneOfVariant(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description("No updates provided")),
          oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("User not found")),
          oneOfVariant(StatusCode.Conflict, jsonBody[ErrorResponse.Conflict].example(nickNameConflictExample))
        )
      )

  val publicUserDataEndpoint: Endpoint[AuthenticationInputs, FUUID, ErrorResponse, PublicUserDataResp, Any] = usersEndpoint
    .summary("Any users data")
    .get
    .securityIn(authRequestParts)
    .in(path[FUUID]("userId") / "data")
    .out(jsonBody[PublicUserDataResp])
    .errorOut(
      oneOf[ErrorResponse](
        unauthorized,
        oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("User data not found"))
      )
    )

  val findUsersEndpoint: Endpoint[
    AuthenticationInputs,
    (Refined[String, And[Trimmed, MinSize[5]]], Paging, Option[Boolean]),
    ErrorResponse,
    (PagingResponseMetadata, List[PublicUserDataResp]),
    Any
  ] = usersEndpoint
    .summary("Find users by nickname")
    .get
    .securityIn(authRequestParts)
    .in(
      "nicknames" / path[String Refined (Trimmed And MinSize[5])]("nickNameStart")
        .description("Start of a nickname. Must have at least 5 characters")
    )
    .in(Paging.params(defaultLimit = 5, maxLimit = 10))
    .in(query[Option[Boolean]]("includeSelf").description("Should the searching user be included in the search results (default true)"))
    .out(PagingResponseMetadata.headers)
    .out(jsonBody[List[PublicUserDataResp]])
    .errorOut(oneOf[ErrorResponse](badRequest, unauthorized))

  val getMultipleUsersEndpoint
    : Endpoint[AuthenticationInputs, Refined[List[FUUID], MaxSize[20]], ErrorResponse, Map[FUUID, Option[PublicUserDataResp]], Any] =
    usersEndpoint
      .summary("Get multiple users data")
      .get
      .securityIn(authRequestParts)
      .in(query[Refined[List[FUUID], MaxSize[20]]]("userId"))
      .out(
        jsonBody[Map[FUUID, Option[PublicUserDataResp]]].example(
          Map(
            FUUID.fuuid("99666bb4-43ef-4258-8f8c-3aafd4e37a7c") -> None,
            FUUID.fuuid("93cc503e-2895-4206-aa45-9a20a3a3ba35") -> Some(
              PublicUserDataResp(FUUID.fuuid("5d0f7802-7555-4160-893b-063becb117e3"), "Alice", Some(PublicUserContactResp(Some("Friend1"))))
            )
          )
        )
      )
      .errorOut(oneOf[ErrorResponse](badRequest, unauthorized))

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

  val updateUserPasswordEndpoint: Endpoint[AuthenticationInputs, UpdatePasswordReq, ErrorResponse, Unit, Any] =
    usersEndpoint
      .summary("Change password")
      .post
      .securityIn(authRequestParts)
      .in("me" / "password")
      .in(jsonBody[UpdatePasswordReq])
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[ErrorResponse](
          unauthorized,
          badRequest,
          oneOfVariant(StatusCode.Forbidden, jsonBody[ErrorResponse.Forbidden]),
          oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("User not found")),
          oneOfVariant(
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

  val requestPasswordResetEndpoint: PublicEndpoint[GeneratePasswordResetReq, ErrorResponse, Unit, Any] =
    usersEndpoint
      .summary("Request password reset")
      .post
      .in("password")
      .in(jsonBody[GeneratePasswordResetReq])
      .out(statusCode(StatusCode.NoContent))
      .errorOut(oneOf[ErrorResponse](badRequest))

  val passwordResetEndpoint: PublicEndpoint[(String, PasswordResetReq), ErrorResponse, Unit, Any] =
    usersEndpoint
      .summary("Reset password using token")
      .put
      .in("password" / path[String]("passwordResetToken"))
      .in(jsonBody[PasswordResetReq])
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[ErrorResponse](badRequest, oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Token not found")))
      )

  val createPersonalApiKeyEndpoint: Endpoint[AuthenticationInputs, NewPersonalApiKeyReq, ErrorResponse, ApiKeyDataResp, Any] =
    usersEndpoint
      .summary("Create personal API key")
      .post
      .securityIn(authRequestParts)
      .in("me" / "api-keys")
      .in(jsonBody[NewPersonalApiKeyReq])
      .out(jsonBody[ApiKeyDataResp])
      .errorOut(oneOf[ErrorResponse](badRequest, unauthorized))

  val listPersonalApiKeysEndpoint: Endpoint[AuthenticationInputs, Unit, ErrorResponse, List[ApiKeyDataResp], Any] = usersEndpoint
    .summary("List users API keys")
    .get
    .securityIn(authRequestParts)
    .in("me" / "api-keys")
    .out(jsonBody[List[ApiKeyDataResp]])
    .errorOut(oneOf[ErrorResponse](unauthorized))

  val deleteApiKeyEndpoint =
    usersEndpoint
      .summary("Delete personal API key")
      .delete
      .securityIn(authRequestParts)
      .in("me" / "api-keys" / path[FUUID]("apiKeyId"))
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("API token not found"))
        )
      )

  val updateApiKeyEndpoint: Endpoint[AuthenticationInputs, (FUUID, UpdateApiKeyDataReq), ErrorResponse, ApiKeyDataResp, Any] =
    usersEndpoint
      .summary("Update personal API key")
      .patch
      .securityIn(authRequestParts)
      .in("me" / "api-keys" / path[FUUID]("apiKeyId"))
      .in(jsonBody[UpdateApiKeyDataReq])
      .out(jsonBody[ApiKeyDataResp])
      .errorOut(
        oneOf[ErrorResponse](
          unauthorized,
          oneOfVariant(StatusCode.BadRequest, jsonBody[ErrorResponse.BadRequest].description("No updates provided")),
          oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("API key not found"))
        )
      )

  val userKeypairEndpoint =
    usersEndpoint
      .summary("Current users key pair")
      .get
      .securityIn(authRequestParts)
      .in("me" / "keypair")
      .out(jsonBody[KeyPairDto])
      .errorOut(
        oneOf[ErrorResponse](
          unauthorized,
          oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Keypair not found for user"))
        )
      )

  private val contactConflictExample =
    Example(Responses.contactConflict, Responses.contactConflict.reason, Responses.contactConflict.reason)

  private val contactAliasConflictExample =
    Example(Responses.contactAliasConflict, Responses.contactAliasConflict.reason, Responses.contactAliasConflict.reason)

  private val addSelfToContactsExample =
    Example(Responses.addSelfToContacts, Responses.addSelfToContacts.reason, Responses.addSelfToContacts.reason)

  val createContactEndpoint: Endpoint[AuthenticationInputs, NewContactReq, ErrorResponse, UserContactResponse, Any] = usersEndpoint
    .summary("Create contact")
    .post
    .securityIn(authRequestParts)
    .in("me" / "contacts")
    .in(jsonBody[NewContactReq])
    .out(jsonBody[UserContactResponse])
    .errorOut(
      oneOf[ErrorResponse](
        unauthorized,
        badRequest,
        oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("User not found")),
        oneOfVariant(
          StatusCode.Conflict,
          jsonBody[ErrorResponse.Conflict].examples(List(contactConflictExample, contactAliasConflictExample))
        ),
        oneOfVariant(StatusCode.PreconditionFailed, jsonBody[ErrorResponse.PreconditionFailed].example(addSelfToContactsExample))
      )
    )

  val listContactsEndpoint =
    usersEndpoint
      .summary("List contacts")
      .get
      .securityIn(authRequestParts)
      .in("me" / "contacts")
      .in(Paging.params())
      .out(PagingResponseMetadata.headers)
      .out(jsonBody[List[UserContactResponse]])
      .errorOut(oneOf[ErrorResponse](badRequest, unauthorized))

  val deleteContactEndpoint: Endpoint[AuthenticationInputs, FUUID, ErrorResponse, Unit, Any] = usersEndpoint
    .summary("Delete contact")
    .delete
    .securityIn(authRequestParts)
    .in("me" / "contacts" / path[FUUID]("contactId"))
    .out(statusCode(StatusCode.NoContent))
    .errorOut(
      oneOf[ErrorResponse](
        badRequest,
        unauthorized,
        oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Contact not found"))
      )
    )

  val editContactEndpoint =
    usersEndpoint
      .summary("Edit contact data")
      .patch
      .securityIn(authRequestParts)
      .in("me" / "contacts" / path[FUUID]("contactId"))
      .in(jsonBody[PatchContactReq])
      .out(jsonBody[UserContactResponse])
      .errorOut(
        oneOf[ErrorResponse](
          badRequest,
          unauthorized,
          oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse.NotFound].description("Contact not found")),
          oneOfVariant(
            StatusCode.Conflict,
            jsonBody[ErrorResponse.Conflict].examples(List(contactConflictExample, contactAliasConflictExample))
          )
        )
      )

  val endpoints: NonEmptyList[Endpoint[_, _, _, _, Any]] =
    NonEmptyList.of(
      registerUserEndpoint,
      getMultipleUsersEndpoint,
      confirmRegistrationEndpoint,
      loginEndpoint,
      logoutEndpoint,
      userDataEndpoint,
      publicUserDataEndpoint,
      findUsersEndpoint,
      updateUserDataEndpoint,
      updateUserPasswordEndpoint,
      requestPasswordResetEndpoint,
      passwordResetEndpoint,
      createPersonalApiKeyEndpoint,
      listPersonalApiKeysEndpoint,
      deleteApiKeyEndpoint,
      updateApiKeyEndpoint,
      userKeypairEndpoint,
      createContactEndpoint,
      listContactsEndpoint,
      deleteContactEndpoint,
      editContactEndpoint
    )

}
