package com.github.huronapp.api.domain.users

import cats.syntax.semigroupk._
import cats.syntax.show._
import com.github.huronapp.api.auth.authentication.HttpAuthentication.HttpAuthentication
import com.github.huronapp.api.auth.authentication.{AuthenticatedUser, HttpAuthentication, SessionRepository}
import com.github.huronapp.api.config.SecurityConfig
import com.github.huronapp.api.auth.authentication.SessionRepository.SessionRepository
import com.github.huronapp.api.domain.users.UsersService.UsersService
import com.github.huronapp.api.domain.users.dto.{
  ApiKeyDataResp,
  PublicUserContactResp,
  PublicUserDataResp,
  UserContactResponse,
  UserDataResp
}
import com.github.huronapp.api.domain.users.dto.fields.{KeyPair => KeyPairDto}
import com.github.huronapp.api.http.{BaseRouter, ErrorResponse}
import com.github.huronapp.api.http.EndpointSyntax._
import com.github.huronapp.api.http.BaseRouter.RouteEffect
import com.github.huronapp.api.http.pagination.PagingResponseMetadata
import io.chrisdavenport.fuuid.FUUID
import io.scalaland.chimney.dsl._
import org.http4s.HttpRoutes
import sttp.model.headers.Cookie.SameSite
import sttp.model.headers.{CookieValueWithMeta, CookieWithMeta}
import zio.logging.Logger
import zio.{Has, URIO, ZIO, ZLayer}
import zio.interop.catz._

object UsersRoutes {

  type UserRoutes = Has[UsersRoutes.Service]

  trait Service {

    val routes: HttpRoutes[RouteEffect]

  }

  val routes: URIO[UserRoutes, HttpRoutes[RouteEffect]] = ZIO.access[UserRoutes](_.get.routes)

  val live: ZLayer[UsersService with Has[Logger[String]] with Has[
    SecurityConfig
  ] with SessionRepository with HttpAuthentication, Nothing, UserRoutes] =
    ZLayer.fromServices[UsersService.Service, Logger[
      String
    ], SecurityConfig, SessionRepository.Service, HttpAuthentication.Service, UsersRoutes.Service](
      (usersService, logger, securityConfig, sessionsRepo, auth) =>
        new Service with BaseRouter {

          private val registerUserRoutes: HttpRoutes[RouteEffect] = UsersEndpoints
            .registerUserEndpoint
            .toRoutes(dto =>
              usersService
                .createUser(dto)
                .flatMapError(error =>
                  logger.warn(show"Unable to register user: ${error.logMessage}").as(UserErrorsMapping.createUserError(error))
                )
                .unit
            )

          private val findUserRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .findUsersEndpoint
              .toAuthenticatedRoutes(
                auth.asUser
              )(user => {
                case (filter, paging, includeSelf, excludeContacts) =>
                  usersService
                    .findUser(
                      user.userId,
                      filter,
                      paging.limit,
                      paging.dropCount,
                      includeSelf,
                      excludeContacts
                    )
                    .map { envelope =>
                      val body = envelope.data.map {
                        case (user, maybeContact) =>
                          PublicUserDataResp(user.id, user.nickName, maybeContact.transformInto[Option[PublicUserContactResp]])
                      }
                      val pagingMetadata = PagingResponseMetadata.of(paging, envelope)
                      (pagingMetadata, body)
                    }
              })

          private val getMultipleUsersRoute: HttpRoutes[RouteEffect] = UsersEndpoints
            .getMultipleUsersEndpoint
            .toAuthenticatedRoutes(auth.asUser)(user =>
              queryUserIds =>
                usersService
                  .getMultipleUsers(user.userId, queryUserIds)
                  .map(result =>
                    result.map {
                      case (userId, maybeUserWithContact) =>
                        (
                          userId,
                          maybeUserWithContact.map {
                            case (user, maybeContact) =>
                              PublicUserDataResp(user.id, user.nickName, maybeContact.map(contact => PublicUserContactResp(contact.alias)))
                          }
                        )
                    }.toMap
                  )
            )

          private val confirmRegistrationRoutes: HttpRoutes[RouteEffect] = UsersEndpoints
            .confirmRegistrationEndpoint
            .toRoutes(
              usersService
                .confirmRegistration(_)
                .unit
                .flatMapError(error =>
                  logger.warn(s"SignUp confirmation failed: ${error.logMessage}").as(UserErrorsMapping.confirmRegistrationError(error))
                )
            )

          private val userLoginRoutes: HttpRoutes[RouteEffect] = UsersEndpoints
            .loginEndpoint
            .toRoutes(dto =>
              usersService
                .verifyCredentials(dto.email, dto.password)
                .flatMapError(error =>
                  logger.warn(s"Credentials verification failed: ${error.logMessage}").as(UserErrorsMapping.loginError(error))
                )
                .flatMap(user => sessionsRepo.sessionCreate(user.id).map(session => (user, session)).orDie)
                .map {
                  case (user, session) =>
                    (
                      user.transformInto[UserDataResp],
                      sessionCookie(session.sessionId.show, securityConfig.sessionCookieTtl.toSeconds),
                      session.csrfToken.show
                    )
                }
            )

          private def sessionCookie(value: String, maxAge: Long): CookieValueWithMeta =
            CookieWithMeta(
              "session",
              value,
              maxAge = Some(maxAge),
              path = Some("/"),
              httpOnly = true,
              secure = securityConfig.sessionCookieSecure,
              sameSite = Some(SameSite.Lax)
            ).valueWithMeta

          private val userLogoutRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints.logoutEndpoint.toAuthenticatedRoutes(auth.asUser) { user => _ =>
              user match {
                case AuthenticatedUser.SessionAuthenticatedUser(session) =>
                  sessionsRepo.deleteSession(session.sessionId).orDie.as(sessionCookie("invalid", 0))
                case _: AuthenticatedUser.ApiKeyUser                     =>
                  ZIO.fail(ErrorResponse.Forbidden("Logout not allowed for this authentication method"))
              }
            }

          private val userDataRoutes: HttpRoutes[RouteEffect] = UsersEndpoints
            .userDataEndpoint
            .toAuthenticatedRoutes(auth.asUser)(user =>
              _ =>
                usersService
                  .userData(user.userId)
                  .someOrFail(ErrorResponse.NotFound("User not found"))
                  .map(_.transformInto[UserDataResp])
                  .map((_, csrfTokenForUser(user)))
            )

          private def csrfTokenForUser(user: AuthenticatedUser): Option[FUUID] =
            user match {
              case AuthenticatedUser.SessionAuthenticatedUser(session) => Some(session.csrfToken)
              case _: AuthenticatedUser.ApiKeyUser                     => None
            }

          private val publicUserDataRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .publicUserDataEndpoint
              .toAuthenticatedRoutes(auth.asUser)(authenticatedUser =>
                objectUserId =>
                  usersService
                    .userContact(authenticatedUser.userId, objectUserId)
                    .someOrFail(ErrorResponse.NotFound("User not found"))
                    .map {
                      case (user, maybeContact) =>
                        PublicUserDataResp(user.id, user.nickName, maybeContact.map(contact => PublicUserContactResp(contact.alias)))
                    }
              )

          private val updateUserRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .updateUserDataEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                dto =>
                  usersService
                    .patchUserData(user.userId, dto)
                    .map(_.transformInto[UserDataResp])
                    .flatMapError(error =>
                      logger.warn(s"Updating user data failed: ${error.logMessage}").as(UserErrorsMapping.patchUserError(error))
                    )
              )

          private val updateUserPasswordRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .updateUserPasswordEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                dto =>
                  usersService
                    .updatePasswordForUser(user.userId, dto)
                    .flatMapError(error =>
                      logger.warn(s"Updating user password failed: ${error.logMessage}").as(UserErrorsMapping.updatePasswordError(error))
                    )
              )

          private val requestPasswordResetRoutes: HttpRoutes[RouteEffect] = UsersEndpoints
            .requestPasswordResetEndpoint
            .toRoutes(dto =>
              usersService
                .requestPasswordResetForUser(dto.email)
                .tapError(error => logger.warn(s"Unable to reset password: ${error.logMessage}"))
                .ignore
            )

          private val passwordResetRoutes: HttpRoutes[RouteEffect] = UsersEndpoints.passwordResetEndpoint.toRoutes {
            case (token, dto) =>
              usersService
                .passwordResetUsingToken(token, dto)
                .unit
                .flatMapError(error =>
                  logger.warn(s"Password reset failed: ${error.logMessage}").as(ErrorResponse.NotFound("Token not found"))
                )
          }

          private val createApiKeyRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .createPersonalApiKeyEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                dto => usersService.createApiKeyForUser(user.userId, dto).map(_.transformInto[ApiKeyDataResp])
              )

          private val listApiKeyRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .listPersonalApiKeysEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                _ => usersService.getApiKeysOf(user.userId, ApiKeyType.Personal).map(_.transformInto[List[ApiKeyDataResp]])
              )

          private val deleteApiKeyRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .deleteApiKeyEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                keyId =>
                  usersService
                    .deleteApiKeyAs(user.userId, keyId)
                    .flatMapError(error =>
                      logger.warn(s"Unable to delete API key: ${error.logMessage}").as(UserErrorsMapping.deleteApiKeyError(error))
                    )
              )

          private val updateApiKey: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .updateApiKeyEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user => {
                case (keyId, dto) =>
                  usersService
                    .updateApiKeyAs(user.userId, keyId, dto)
                    .map(_.transformInto[ApiKeyDataResp])
                    .flatMapError(error =>
                      logger.warn(s"Unable to update API key: ${error.logMessage}").as(UserErrorsMapping.updateApiKeyError(error))
                    )
              })

          private val getUsersKeyPair: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .userKeypairEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                _ =>
                  usersService
                    .getKeyPairOf(user.userId)
                    .someOrFail(ErrorResponse.NotFound("Key pair not found"))
                    .map(_.transformInto[KeyPairDto])
              )

          private val createContactRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .createContactEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                req =>
                  usersService
                    .createContactAs(user.userId, req)
                    .map {
                      case (contact, user) => UserContactResponse(user.id, user.nickName, contact.alias)
                    }
                    .flatMapError(error =>
                      logger.warn(show"Unable to create contact: ${error.logMessage}").as(UserErrorsMapping.createContactError(error))
                    )
              )

          private val listContactsRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .listContactsEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user => {
                case (paging, nameFilter) =>
                  usersService.listContactsAs(user.userId, paging.limit, paging.dropCount, nameFilter).map { envelope =>
                    val body = envelope.data.map {
                      case (contact, user) => UserContactResponse(user.id, user.nickName, contact.alias)
                    }
                    val headers = PagingResponseMetadata.of(paging, envelope)
                    (headers, body)
                  }
              })

          private val deleteContactRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .deleteContactEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user =>
                contactId =>
                  usersService
                    .deleteContactAs(user.userId, contactId)
                    .filterOrFail(identity)(ErrorResponse.NotFound("Contact not found"))
                    .unit
              )

          private val updateContactRoutes: HttpRoutes[RouteEffect] =
            UsersEndpoints
              .editContactEndpoint
              .toAuthenticatedRoutes(auth.asUser)(user => {
                case (contactObjectId, dto) =>
                  usersService
                    .patchContactAs(user.userId, contactObjectId, dto)
                    .map {
                      case (contact, user) => UserContactResponse(user.id, user.nickName, contact.alias)
                    }
                    .flatMapError(error =>
                      logger.warn(show"Unable to update contact data: ${error.logMessage}").as(UserErrorsMapping.editContactError(error))
                    )
              })

          override val routes: HttpRoutes[RouteEffect] =
            registerUserRoutes <+>
              getMultipleUsersRoute <+>
              confirmRegistrationRoutes <+>
              userLoginRoutes <+>
              userLogoutRoutes <+>
              userDataRoutes <+>
              findUserRoutes <+>
              updateUserRoutes <+>
              publicUserDataRoutes <+>
              updateUserPasswordRoutes <+>
              requestPasswordResetRoutes <+>
              passwordResetRoutes <+>
              createApiKeyRoutes <+>
              listApiKeyRoutes <+>
              deleteApiKeyRoutes <+>
              updateApiKey <+>
              getUsersKeyPair <+>
              createContactRoutes <+>
              listContactsRoutes <+>
              deleteContactRoutes <+>
              updateContactRoutes

        }
    )

}
