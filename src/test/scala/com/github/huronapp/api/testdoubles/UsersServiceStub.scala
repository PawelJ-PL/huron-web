package com.github.huronapp.api.testdoubles

import com.github.huronapp.api.constants.{MiscConstants, Users}
import com.github.huronapp.api.domain.users.UsersService.UsersService
import com.github.huronapp.api.domain.users.dto.{
  NewContactReq,
  NewPersonalApiKeyReq,
  NewUserReq,
  PasswordResetReq,
  PatchContactReq,
  PatchUserDataReq,
  UpdateApiKeyDataReq,
  UpdatePasswordReq
}
import com.github.huronapp.api.domain.users.{
  ApiKey,
  ApiKeyType,
  ContactWithUser,
  CreateContactError,
  CreateUserError,
  CredentialsVerificationError,
  DeleteApiKeyError,
  EditContactError,
  Email,
  KeyPair,
  PasswordResetError,
  PatchUserError,
  RequestPasswordResetError,
  SignUpConfirmationError,
  TemporaryToken,
  TokenType,
  UpdateApiKeyError,
  UpdatePasswordError,
  User,
  UserContact,
  UserWithContact,
  UsersService
}
import com.github.huronapp.api.http.pagination.PaginationEnvelope
import eu.timepit.refined.api.Refined
import eu.timepit.refined.{boolean, string}
import eu.timepit.refined.collection.{MaxSize, MinSize}
import eu.timepit.refined.numeric.Positive
import io.chrisdavenport.fuuid.FUUID
import zio.{ULayer, ZIO, ZLayer}

object UsersServiceStub extends Users with MiscConstants {

  final case class UsersServiceResponses(
    createUser: ZIO[Any, CreateUserError, User] = ZIO.succeed(ExampleUser),
    findUser: ZIO[Any, Nothing, PaginationEnvelope[UserWithContact]] = ZIO.succeed(PaginationEnvelope(List((ExampleUser,
            Some(ExampleContact))), 1)),
    confirmRegistration: ZIO[Any, SignUpConfirmationError, FUUID] = ZIO.succeed(ExampleUserId),
    login: ZIO[Any, CredentialsVerificationError, User] = ZIO.succeed(ExampleUser),
    userData: ZIO[Any, Nothing, Option[User]] = ZIO.some(ExampleUser),
    patchUser: ZIO[Any, PatchUserError, User] = ZIO.succeed(ExampleUser),
    updatePassword: ZIO[Any, UpdatePasswordError, Unit] = ZIO.unit,
    passwordResetRequest: ZIO[Any, RequestPasswordResetError, TemporaryToken] = ZIO.succeed(TemporaryToken("abc", ExampleUserId,
        TokenType.PasswordReset)),
    passwordReset: ZIO[Any, PasswordResetError, Unit] = ZIO.unit,
    createApiKey: ZIO[Any, Nothing, ApiKey] = ZIO.succeed(ExampleApiKey),
    listApiKeys: ZIO[Any, Nothing, List[ApiKey]] = ZIO.succeed(List(ExampleApiKey)),
    deleteApiKey: ZIO[Any, DeleteApiKeyError, Unit] = ZIO.unit,
    updateApiKey: ZIO[Any, UpdateApiKeyError, ApiKey] = ZIO.succeed(ExampleApiKey),
    keyPair: ZIO[Any, Nothing, Option[KeyPair]] = ZIO.some(ExampleKeyPair),
    contactData: ZIO[Any, Nothing, Option[UserWithContact]] = ZIO.some((ExampleUser.copy(id = ExampleContact.contactId, nickName = "user2"),
        Some(ExampleContact))),
    createContact: ZIO[Any, CreateContactError, ContactWithUser] = ZIO.succeed((ExampleContact, ExampleUser.copy(id =
            ExampleContact.contactId, nickName = "user2"))),
    listContacts: ZIO[Any, Nothing, PaginationEnvelope[ContactWithUser]] = ZIO.succeed(PaginationEnvelope(List((ExampleContact,
            ExampleUser)), 1)),
    deleteContact: ZIO[Any, Nothing, Boolean] = ZIO.succeed(true),
    editContact: ZIO[Any, EditContactError, ContactWithUser] = ZIO.succeed((ExampleContact, ExampleUser.copy(id = ExampleFuuid1))),
    getMultipleUsers: ZIO[Any, Nothing, List[(FUUID, Option[UserWithContact])]] = ZIO.succeed(List((ExampleUserId, Some((ExampleUser,
              Some(UserContact(ExampleFuuid1, ExampleUserId, ExampleContact.alias))))), (ExampleFuuid1, None))))

  def withResponses(responses: UsersServiceResponses): ULayer[UsersService] =
    ZLayer.succeed(new UsersService.Service {

      override def createUser(dto: NewUserReq): ZIO[Any, CreateUserError, User] = responses.createUser

      override def confirmRegistration(token: String): ZIO[Any, SignUpConfirmationError, FUUID] = responses.confirmRegistration

      override def verifyCredentials(email: Email, password: String): ZIO[Any, CredentialsVerificationError, User] = responses.login

      override def userData(userId: FUUID): ZIO[Any, Nothing, Option[User]] = responses.userData

      override def userContact(ownerId: FUUID, contactId: FUUID): ZIO[Any, Nothing, Option[UserWithContact]] =
        responses.contactData

      override def findUser(
        asUser: FUUID,
        nickNamePart: Refined[String, boolean.And[string.Trimmed, MinSize[5]]],
        limit: Refined[Int, Positive],
        drop: Int,
        includeSelf: Boolean
      ): ZIO[Any, Nothing, PaginationEnvelope[UserWithContact]] = responses.findUser

      override def getMultipleUsers(
        asUser: FUUID,
        userIds: Refined[List[FUUID], MaxSize[20]]
      ): ZIO[Any, Nothing, List[(FUUID, Option[UserWithContact])]] = responses.getMultipleUsers

      override def createContactAs(userId: FUUID, dto: NewContactReq): ZIO[Any, CreateContactError, ContactWithUser] =
        responses.createContact

      override def listContactsAs(
        userId: FUUID,
        limit: Refined[Int, Positive],
        drop: Int,
        nameFilter: Option[String]
      ): ZIO[Any, Nothing, PaginationEnvelope[(UserContact, User)]] = responses.listContacts

      override def deleteContactAs(userId: FUUID, contactObjectId: FUUID): ZIO[Any, Nothing, Boolean] = responses.deleteContact

      override def patchContactAs(
        userId: FUUID,
        contactObjectId: FUUID,
        dto: PatchContactReq
      ): ZIO[Any, EditContactError, (UserContact, User)] = responses.editContact

      override def patchUserData(userId: FUUID, dto: PatchUserDataReq): ZIO[Any, PatchUserError, User] = responses.patchUser

      override def updatePasswordForUser(userId: FUUID, dto: UpdatePasswordReq): ZIO[Any, UpdatePasswordError, Unit] =
        responses.updatePassword

      override def requestPasswordResetForUser(email: Email): ZIO[Any, RequestPasswordResetError, TemporaryToken] =
        responses.passwordResetRequest

      override def passwordResetUsingToken(tokenValue: String, dto: PasswordResetReq): ZIO[Any, PasswordResetError, Unit] =
        responses.passwordReset

      override def createApiKeyForUser(userId: FUUID, dto: NewPersonalApiKeyReq): ZIO[Any, Nothing, ApiKey] = responses.createApiKey

      override def getApiKeysOf(userId: FUUID, keyType: ApiKeyType): ZIO[Any, Nothing, List[ApiKey]] = responses.listApiKeys

      override def deleteApiKeyAs(userId: FUUID, keyId: FUUID): ZIO[Any, DeleteApiKeyError, Unit] = responses.deleteApiKey

      override def updateApiKeyAs(userId: FUUID, keyId: FUUID, dto: UpdateApiKeyDataReq): ZIO[Any, UpdateApiKeyError, ApiKey] =
        responses.updateApiKey

      override def getKeyPairOf(userId: FUUID): ZIO[Any, Nothing, Option[KeyPair]] = responses.keyPair

    })

}
