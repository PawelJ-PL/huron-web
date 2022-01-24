package com.github.huronapp.api.domain.users

import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.auth.authorization.{AuthorizationKernel, OperationNotPermitted, SetEncryptionKey}
import com.github.huronapp.api.constants.{Collections, Config, MiscConstants, Users}
import com.github.huronapp.api.domain.collections.EncryptionKey
import com.github.huronapp.api.domain.collections.dto.EncryptionKeyData
import com.github.huronapp.api.domain.users.UsersService.UsersService
import com.github.huronapp.api.domain.users.dto.fields.{
  ApiKeyDescription,
  ContactAlias,
  Nickname,
  Password,
  PrivateKey,
  PublicKey,
  KeyPair => KeyPairDto
}
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
import com.github.huronapp.api.http.pagination.PaginationEnvelope
import com.github.huronapp.api.messagebus.InternalMessage
import com.github.huronapp.api.testdoubles.CollectionsRepoFake.UserCollection
import com.github.huronapp.api.testdoubles.{
  CollectionsRepoFake,
  CryptoStub,
  InternalTopicFake,
  KamonTracingFake,
  RandomUtilsStub,
  UsersRepoFake
}
import com.github.huronapp.api.utils.OptionalValue
import eu.timepit.refined.api.Refined
import io.github.gaelrenoux.tranzactio.doobie.Database
import kamon.context.Context
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{Ref, ZLayer}
import zio.logging.slf4j.Slf4jLogger
import zio.test.Assertion.{equalTo, hasSameElements, isEmpty, isLeft, isNone, isSome}
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert}
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.MaxSize
import io.chrisdavenport.fuuid.FUUID

import java.time.Instant

object UserServiceSpec extends DefaultRunnableSpec with Users with Config with MiscConstants with Collections {

  private val logger = Slf4jLogger.make((_, str) => str)

  private def createUsersService(
    usersRepoRef: Ref[UsersRepoFake.UsersRepoState],
    internalTopic: Ref[List[InternalMessage]],
    collectionsRepoRef: Ref[CollectionsRepoFake.CollectionsRepoState]
  ): ZLayer[Blocking with Clock, Nothing, UsersService] = {
    val collectionsRep = CollectionsRepoFake.create(collectionsRepoRef)
    CryptoStub.create ++ UsersRepoFake.create(usersRepoRef) ++ Database.none ++ RandomUtilsStub.create ++ logger ++ InternalTopicFake
      .usingRef(internalTopic) ++ ZLayer.succeed(
      ExampleSecurityConfig
    ) ++ KamonTracingFake.noOp ++ collectionsRep ++ (Database.none ++ collectionsRep >>> AuthorizationKernel.live) >>> UsersService.live
  }

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Users service suite")(
      createUser,
      createUserWithEmailConflict,
      createUserWithNickNameConflict,
      findUsersByNickname,
      findUsersByNicknameWithLimit,
      findUsersByNicknameWithDrop,
      findUsersByNicknameWithoutSelf,
      findUsersByNicknameExcludingContacts,
      getMultipleUsers,
      confirmSignUp,
      confirmSignUpWithInvalidToken,
      confirmAlreadyConfirmedRegistration,
      verifyCredentials,
      verifyCredentialsForNonExistingEmail,
      verifyCredentialsForInvalidPassword,
      verifyCredentialsForInactiveUser,
      getUserData,
      getUserDataForNonExistingUser,
      getUserContact,
      getPublicDataIfContactNotDefined,
      getUserContactForNonExistingUser,
      createContact,
      createContactAliasAlreadyExists,
      createContactAlreadyExists,
      createContactUserNotFound,
      createContactAddSelf,
      listContacts,
      listContactsWithLimit,
      listContactsWithDrop,
      listContactsWithNameFilter,
      updateContact,
      updateContactNoUpdates,
      updateContactNotFound,
      updateContactAliasExists,
      updateUserData,
      updateUserDataWithNoUpdates,
      updateUserDataWithNickNameConflict,
      updateUserDataWithMissingUser,
      updatePassword,
      updatePasswordWithMissingEmail,
      updatePasswordWithInvalidCredentials,
      updatePasswordWithInvalidEmail,
      updatePasswordWithTheSameValue,
      updatePasswordForInactiveUser,
      updatePasswordForNonExistingUser,
      updatePasswordWithMissingEncryptionKey,
      updatePasswordWithNotOwnedCollectionKey,
      updatePasswordWithInvalidKeyVersion,
      passwordResetRequest,
      passwordResetRequestForNonExistingEmail,
      passwordResetRequestForInactiveUser,
      passwordResetWithToken,
      passwordResetWithTokenForInactiveUser,
      passwordResetWithTokenForInvalidEmail,
      createApiKey,
      getApiKeys,
      deleteApiKey,
      deleteNonExistingApiKey,
      deleteApiKeyOwnedByAnotherUser,
      updateApiKey,
      updateApiKeyWithEmptyUpdatesSet,
      updateNonExistingApiKey,
      updateApiKeyOwnedByAnotherUser,
      getKeyPair,
      getKeyPairIfNotExists
    )

  private val keyPairDto = KeyPairDto(KeyAlgorithm.Rsa, PublicKey(ExamplePublicKey), PrivateKey(ExamplePrivateKey))

  private val newUserDto =
    NewUserReq(Nickname(ExampleUserNickName), ExampleUserEmail, Password(ExampleUserPassword), Some(ExampleUserLanguage), keyPairDto)

  private val encryptionKeyData = EncryptionKeyData(ExampleCollectionId, ExampleEncryptionKeyValue, ExampleEncryptionKeyVersion)

  private val editContactDto = PatchContactReq(Some(OptionalValue.of(ContactAlias("newAlias"))))

  private val createUser = testM("should successfully create a user") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(UsersRepoFake.UsersRepoState())
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      user                  <- UsersService.createUser(newUserDto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      expectedUser = User(FirstRandomFuuid, "digest(alice@example.org)", ExampleUserNickName, Language.Pl)
      expectedToken = "000102030405060708090a0b0c0d0e0f1011121314151617"
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user)(equalTo(expectedUser)) &&
      assert(finalUsersRepoState.users)(hasSameElements(Set(expectedUser))) &&
      assert(finalUsersRepoState.auth)(
        hasSameElements(Set(UserAuth(FirstRandomFuuid, "bcrypt(secret-password)", confirmed = false, enabled = true)))
      ) &&
      assert(finalUsersRepoState.tokens)(hasSameElements(Set(TemporaryToken(expectedToken, FirstRandomFuuid, TokenType.Registration)))) &&
      assert(sentMessages)(
        hasSameElements(List(InternalMessage.UserRegistered(expectedUser, ExampleUserEmail, expectedToken, Some(Context.Empty))))
      ) &&
      assert(finalUsersRepoState.keyPairs)(hasSameElements(Set(ExampleKeyPair.copy(id = SecondRandomFuuid, userId = FirstRandomFuuid)))) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val createUserWithEmailConflict = testM("should not create user if email registered") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState =
        UsersRepoFake.UsersRepoState(users = Set(ExampleUser.copy(emailHash = "digest(alice@example.org)", nickName = "SomeOtherNickName")))
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService.createUser(newUserDto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(EmailAlreadyRegistered("digest(alice@example.org)")))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val createUserWithNickNameConflict = testM("should not create user if nickname registered") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser))
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService.createUser(newUserDto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(NickNameAlreadyRegistered(ExampleUserNickName)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val findUsersByNickname = testM("should return users with matching nickname") {
    val user1 = ExampleUser.copy(nickName = "aaabcdefgh")
    val user2 = ExampleUser.copy(id = ExampleFuuid1, nickName = "fooaaabc")
    val user3 = ExampleUser.copy(id = ExampleFuuid2, nickName = "aaabcaaaaaaaaaaaaaaaaaa")
    val user4 = ExampleUser.copy(id = ExampleFuuid3, nickName = "aaabc")
    val user5 = ExampleUser.copy(id = ExampleFuuid4, nickName = "aaabca")

    val initialState = UsersRepoFake.UsersRepoState(users = Set(user1, user2, user3, user4, user5))
    val collectionRepoState = CollectionsRepoFake.CollectionsRepoState()

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initialState)
      collectionsRepo       <- Ref.make(collectionRepoState)
      user                  <- UsersService
                                 .findUser(ExampleUserId, "aaabc", 30, 0, includeSelf = true, excludeContacts = false)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user.data)(
      equalTo(
        List(
          (user4, None),
          (user5, None),
          (user1, None),
          (user3, None)
        )
      )
    ) &&
      assert(finalUsersRepoState)(equalTo(initialState)) &&
      assert(finalCollectionsState)(equalTo(collectionRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val findUsersByNicknameWithLimit = testM("should return users with matching nickname with limit") {
    val user1 = ExampleUser.copy(nickName = "aaabcdefgh")
    val user2 = ExampleUser.copy(id = ExampleFuuid1, nickName = "fooaaabc")
    val user3 = ExampleUser.copy(id = ExampleFuuid2, nickName = "aaabcaaaaaaaaaaaaaaaaaa")
    val user4 = ExampleUser.copy(id = ExampleFuuid3, nickName = "aaabc")
    val user5 = ExampleUser.copy(id = ExampleFuuid4, nickName = "aaabca")

    val initialState = UsersRepoFake.UsersRepoState(users = Set(user1, user2, user3, user4, user5))
    val collectionRepoState = CollectionsRepoFake.CollectionsRepoState()

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initialState)
      collectionsRepo       <- Ref.make(collectionRepoState)
      user                  <- UsersService
                                 .findUser(ExampleUserId, "aaabc", 2, 0, includeSelf = true, excludeContacts = false)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user.data)(
      equalTo(
        List(
          (user4, None),
          (user5, None)
        )
      )
    ) &&
      assert(finalUsersRepoState)(equalTo(initialState)) &&
      assert(finalCollectionsState)(equalTo(collectionRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val findUsersByNicknameWithDrop = testM("should return users with matching nickname with drop") {
    val user1 = ExampleUser.copy(nickName = "aaabcdefgh")
    val user2 = ExampleUser.copy(id = ExampleFuuid1, nickName = "fooaaabc")
    val user3 = ExampleUser.copy(id = ExampleFuuid2, nickName = "aaabcaaaaaaaaaaaaaaaaaa")
    val user4 = ExampleUser.copy(id = ExampleFuuid3, nickName = "aaabc")
    val user5 = ExampleUser.copy(id = ExampleFuuid4, nickName = "aaabca")

    val initialState = UsersRepoFake.UsersRepoState(users = Set(user1, user2, user3, user4, user5))
    val collectionRepoState = CollectionsRepoFake.CollectionsRepoState()

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initialState)
      collectionsRepo       <- Ref.make(collectionRepoState)
      user                  <- UsersService
                                 .findUser(ExampleUserId, "aaabc", 30, 2, includeSelf = true, excludeContacts = false)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user.data)(
      equalTo(
        List(
          (user1, None),
          (user3, None)
        )
      )
    ) &&
      assert(finalUsersRepoState)(equalTo(initialState)) &&
      assert(finalCollectionsState)(equalTo(collectionRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val findUsersByNicknameWithoutSelf = testM("should return users with matching nickname without self") {
    val user1 = ExampleUser.copy(nickName = "aaabcdefgh")
    val user2 = ExampleUser.copy(id = ExampleFuuid1, nickName = "fooaaabc")
    val user3 = ExampleUser.copy(id = ExampleFuuid2, nickName = "aaabcaaaaaaaaaaaaaaaaaa")
    val user4 = ExampleUser.copy(id = ExampleFuuid3, nickName = "aaabc")
    val user5 = ExampleUser.copy(id = ExampleFuuid4, nickName = "aaabca")

    val initialState = UsersRepoFake.UsersRepoState(users = Set(user1, user2, user3, user4, user5))
    val collectionRepoState = CollectionsRepoFake.CollectionsRepoState()

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initialState)
      collectionsRepo       <- Ref.make(collectionRepoState)
      user                  <- UsersService
                                 .findUser(ExampleUserId, "aaabc", 30, 0, includeSelf = false, excludeContacts = false)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user.data)(
      equalTo(
        List(
          (user4, None),
          (user5, None),
          (user3, None)
        )
      )
    ) &&
      assert(finalUsersRepoState)(equalTo(initialState)) &&
      assert(finalCollectionsState)(equalTo(collectionRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val findUsersByNicknameExcludingContacts = testM("should return users with matching nickname excluding contacts") {
    val user1 = ExampleUser.copy(nickName = "aaabcdefgh")
    val user2 = ExampleUser.copy(id = ExampleFuuid1, nickName = "fooaaabc")
    val user3 = ExampleUser.copy(id = ExampleFuuid2, nickName = "aaabcaaaaaaaaaaaaaaaaaa")
    val user4 = ExampleUser.copy(id = ExampleFuuid3, nickName = "aaabc")
    val user5 = ExampleUser.copy(id = ExampleFuuid4, nickName = "aaabca")

    val contact1 = UserContact(ExampleUserId, user1.id, None)
    val contact2 = UserContact(ExampleUserId, user4.id, None)

    val initialState = UsersRepoFake.UsersRepoState(
      users = Set(user1, user2, user3, user4, user5),
      contacts = Set(contact1, contact2)
    )
    val collectionRepoState = CollectionsRepoFake.CollectionsRepoState()

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initialState)
      collectionsRepo       <- Ref.make(collectionRepoState)
      user                  <- UsersService
                                 .findUser(ExampleUserId, "aaabc", 30, 0, includeSelf = true, excludeContacts = true)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user.data)(
      equalTo(
        List(
          (user5, None),
          (user3, None)
        )
      )
    ) &&
      assert(finalUsersRepoState)(equalTo(initialState)) &&
      assert(finalCollectionsState)(equalTo(collectionRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val getMultipleUsers = testM("should return multiple users with missing values") {
    val user1 = ExampleUser.copy(nickName = "user1")
    val user2 = ExampleUser.copy(id = ExampleFuuid1, nickName = "user2")
    val user3 = ExampleUser.copy(id = ExampleFuuid2, nickName = "user3")
    val user4 = ExampleUser.copy(id = ExampleFuuid3, nickName = "user4")
    val user5 = ExampleUser.copy(id = ExampleFuuid4, nickName = "user5")

    val initialState = UsersRepoFake.UsersRepoState(users = Set(user1, user2, user3, user4, user5))
    val collectionRepoState = CollectionsRepoFake.CollectionsRepoState()

    val userIds = Refined.unsafeApply[List[FUUID], MaxSize[20]](List(ExampleFuuid1, ExampleFuuid5, ExampleFuuid3, ExampleFuuid6))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initialState)
      collectionsRepo       <- Ref.make(collectionRepoState)
      users                 <- UsersService
                                 .getMultipleUsers(ExampleUserId, userIds)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(users)(
      hasSameElements(
        List((ExampleFuuid1, Some((user2, None))), (ExampleFuuid5, None), (ExampleFuuid3, Some((user4, None))), (ExampleFuuid6, None))
      )
    ) &&
      assert(finalUsersRepoState)(equalTo(initialState)) &&
      assert(finalCollectionsState)(equalTo(collectionRepoState)) &&
      assert(sentMessages)(isEmpty)
  }

  private val confirmSignUp = testM("should confirm registration") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             auth = Set(UserAuth(ExampleUserId, "passHash", confirmed = false, enabled = true)),
                             tokens = Set(TemporaryToken("abc", ExampleUserId, TokenType.Registration))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      userId                <- UsersService.confirmRegistration("abc").provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(userId)(equalTo(ExampleUserId)) &&
      assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(
        hasSameElements(Set(UserAuth(ExampleUserId, "passHash", confirmed = true, enabled = true)))
      ) &&
      assert(finalUsersRepoState.tokens)(isEmpty) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val confirmSignUpWithInvalidToken = testM("should not confirm registration if token is invalid") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(auth = Set(UserAuth(ExampleUserId, "passHash", confirmed = false, enabled = true)))
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService.confirmRegistration("abc").provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(NoValidTokenFound("abc")))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val confirmAlreadyConfirmedRegistration = testM("should not confirm registration if already confirmed") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             auth = Set(UserAuth(ExampleUserId, "passHash", confirmed = true, enabled = true)),
                             tokens = Set(TemporaryToken("abc", ExampleUserId, TokenType.Registration))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService.confirmRegistration("abc").provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(RegistrationAlreadyConfirmed(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val verifyCredentials = testM("should verify valid credentials") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      user                  <- UsersService
                                 .verifyCredentials(ExampleUserEmail, ExampleUserPassword)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user)(equalTo(ExampleUser)) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val verifyCredentialsForNonExistingEmail = testM("should fail on credentials validation if email not found") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .verifyCredentials(Email("foo@bar"), ExampleUserPassword)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(EmailNotFound("digest(foo@bar)")))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val verifyCredentialsForInvalidPassword = testM("should fail on credentials validation if password is incorrect") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .verifyCredentials(ExampleUserEmail, "invalid-password")
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(InvalidPassword(ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val verifyCredentialsForInactiveUser = testM("should fail on credentials validation if user is not active") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = false))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .verifyCredentials(ExampleUserEmail, ExampleUserPassword)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(UserIsNotActive(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val getUserData = testM("should get users data") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser))
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      user                  <- UsersService.userData(ExampleUserId).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user)(isSome(equalTo(ExampleUser))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val getUserDataForNonExistingUser = testM("should get None if user not exists") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState()
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      user                  <- UsersService.userData(ExampleUserId).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user)(isNone) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val getUserContact = testM("should get users contacts") {
    val secondUser = User(ExampleContact.contactId, "someEmailHash", "testNickName", Language.Pl)

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser, secondUser), contacts = Set(ExampleContact))
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      user                  <- UsersService
                                 .userContact(ExampleUserId, ExampleContact.contactId)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user)(isSome(equalTo((secondUser, Some(ExampleContact))))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val getPublicDataIfContactNotDefined = testM("should get only user public data if contact is not defined") {
    val secondUser = User(ExampleContact.contactId, "someEmailHash", "testNickName", Language.Pl)

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser, secondUser), contacts = Set.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      user                  <- UsersService
                                 .userContact(ExampleUserId, ExampleContact.contactId)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user)(isSome(equalTo((secondUser, None)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val getUserContactForNonExistingUser = testM("should get None if user not found") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser))
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      user                  <- UsersService
                                 .userContact(ExampleUserId, ExampleContact.contactId)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user)(isNone) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val createContact = testM("should create contact") {
    val dto = NewContactReq(ExampleContact.contactId, ExampleContact.alias.map(ContactAlias(_)))

    val secondUser = ExampleUser.copy(id = ExampleContact.contactId)

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser, secondUser))
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService.createContactAs(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(equalTo((ExampleContact, secondUser))) &&
      assert(finalUsersRepoState.contacts)(hasSameElements(Set(ExampleContact))) &&
      assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(equalTo(initUsersRepoState.auth)) &&
      assert(finalUsersRepoState.tokens)(equalTo(initUsersRepoState.tokens)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val createContactAliasAlreadyExists = testM("should not create contact when alias already exists") {
    val dto = NewContactReq(ExampleContact.contactId, ExampleContact.alias.map(ContactAlias(_)))

    val secondUser = ExampleUser.copy(id = ExampleContact.contactId)

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser, secondUser),
                             contacts = Set(UserContact(ExampleUserId, ExampleFuuid1, ExampleContact.alias))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      collectionsRepo     <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result              <-
        UsersService.createContactAs(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState <- usersRepo.get
    } yield assert(result)(isLeft(equalTo(ContactAliasAlreadyExists(ExampleUserId, "Teddy", ExampleFuuid1)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState))
  }

  private val createContactAlreadyExists = testM("should not create contact when already exists") {
    val dto = NewContactReq(ExampleContact.contactId, Some(ContactAlias("Carol")))

    val secondUser = ExampleUser.copy(id = ExampleContact.contactId)

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser, secondUser),
                             contacts = Set(ExampleContact)
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      collectionsRepo     <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result              <-
        UsersService.createContactAs(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState <- usersRepo.get
    } yield assert(result)(isLeft(equalTo(ContactAlreadyExists(ExampleUserId, ExampleContact.contactId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState))
  }

  private val createContactUserNotFound = testM("should not create contact when user not found") {
    val dto = NewContactReq(ExampleContact.contactId, Some(ContactAlias("OtherAlias")))

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             contacts = Set(UserContact(ExampleUserId, ExampleFuuid1, ExampleContact.alias))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      collectionsRepo     <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result              <-
        UsersService.createContactAs(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState <- usersRepo.get
    } yield assert(result)(isLeft(equalTo(UserNotFound(ExampleContact.contactId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState))
  }

  private val createContactAddSelf = testM("should not create contact adding self") {
    val dto = NewContactReq(ExampleUserId, Some(ContactAlias("OtherAlias")))

    val secondUser = ExampleUser.copy(id = ExampleContact.contactId)

    for {
      internalTopic       <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser, secondUser),
                             contacts = Set(UserContact(ExampleUserId, ExampleFuuid1, ExampleContact.alias))
                           )
      usersRepo           <- Ref.make(initUsersRepoState)
      collectionsRepo     <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result              <-
        UsersService.createContactAs(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState <- usersRepo.get
    } yield assert(result)(isLeft(equalTo(ForbiddenSelfToContacts(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState))
  }

  private val listContacts = testM("should list contacts") {
    val user1 = ExampleUser.copy(id = ExampleFuuid1, nickName = "Alice")
    val user2 = ExampleUser.copy(id = ExampleFuuid2, nickName = "Bob")
    val user3 = ExampleUser.copy(id = ExampleFuuid3, nickName = "Carol")
    val user4 = ExampleUser.copy(id = ExampleFuuid4, nickName = "Dave")
    val user5 = ExampleUser.copy(id = ExampleFuuid5, nickName = "Eve")
    val user6 = ExampleUser.copy(id = ExampleFuuid6, nickName = "Frank")
    val user7 = ExampleUser.copy(id = ExampleFuuid7, nickName = "Grace")
    val user8 = ExampleUser.copy(id = ExampleFuuid8, nickName = "Heidi")

    val contact1 = UserContact(ExampleUserId, user1.id, None)
    val contact2 = UserContact(ExampleUserId, user3.id, Some("Carlos"))
    val contact3 = UserContact(user1.id, user4.id, Some("Dan"))
    val contact4 = UserContact(ExampleUserId, user5.id, Some("Ivan"))
    val contact5 = UserContact(ExampleUserId, user7.id, Some("Adam"))
    val contact6 = UserContact(ExampleUserId, user8.id, None)

    val initRepo = UsersRepoFake.UsersRepoState(
      users = Set(user1, user2, user3, user4, user5, user6, user7, user8),
      contacts = Set(contact1, contact2, contact3, contact4, contact5, contact6)
    )

    for {
      internalTopic   <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo       <- Ref.make(initRepo)
      collectionsRepo <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result          <-
        UsersService.listContactsAs(ExampleUserId, 30, 0, None).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
    } yield assert(result)(
      equalTo(
        PaginationEnvelope(
          List(
            (contact5, user7),
            (contact2, user3),
            (contact4, user5),
            (contact1, user1),
            (contact6, user8)
          ),
          5
        )
      )
    )
  }

  private val listContactsWithLimit = testM("should list contacts with limit") {
    val user1 = ExampleUser.copy(id = ExampleFuuid1, nickName = "Alice")
    val user2 = ExampleUser.copy(id = ExampleFuuid2, nickName = "Bob")
    val user3 = ExampleUser.copy(id = ExampleFuuid3, nickName = "Carol")
    val user4 = ExampleUser.copy(id = ExampleFuuid4, nickName = "Dave")
    val user5 = ExampleUser.copy(id = ExampleFuuid5, nickName = "Eve")
    val user6 = ExampleUser.copy(id = ExampleFuuid6, nickName = "Frank")
    val user7 = ExampleUser.copy(id = ExampleFuuid7, nickName = "Grace")
    val user8 = ExampleUser.copy(id = ExampleFuuid8, nickName = "Heidi")

    val contact1 = UserContact(ExampleUserId, user1.id, None)
    val contact2 = UserContact(ExampleUserId, user3.id, Some("Carlos"))
    val contact3 = UserContact(user1.id, user4.id, Some("Dan"))
    val contact4 = UserContact(ExampleUserId, user5.id, Some("Ivan"))
    val contact5 = UserContact(ExampleUserId, user7.id, Some("Adam"))
    val contact6 = UserContact(ExampleUserId, user8.id, None)

    val initRepo = UsersRepoFake.UsersRepoState(
      users = Set(user1, user2, user3, user4, user5, user6, user7, user8),
      contacts = Set(contact1, contact2, contact3, contact4, contact5, contact6)
    )

    for {
      internalTopic   <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo       <- Ref.make(initRepo)
      collectionsRepo <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result          <-
        UsersService.listContactsAs(ExampleUserId, 3, 0, None).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
    } yield assert(result)(
      equalTo(
        PaginationEnvelope(
          List(
            (contact5, user7),
            (contact2, user3),
            (contact4, user5)
          ),
          5
        )
      )
    )
  }

  private val listContactsWithDrop = testM("should list contacts with drop") {
    val user1 = ExampleUser.copy(id = ExampleFuuid1, nickName = "Alice")
    val user2 = ExampleUser.copy(id = ExampleFuuid2, nickName = "Bob")
    val user3 = ExampleUser.copy(id = ExampleFuuid3, nickName = "Carol")
    val user4 = ExampleUser.copy(id = ExampleFuuid4, nickName = "Dave")
    val user5 = ExampleUser.copy(id = ExampleFuuid5, nickName = "Eve")
    val user6 = ExampleUser.copy(id = ExampleFuuid6, nickName = "Frank")
    val user7 = ExampleUser.copy(id = ExampleFuuid7, nickName = "Grace")
    val user8 = ExampleUser.copy(id = ExampleFuuid8, nickName = "Heidi")

    val contact1 = UserContact(ExampleUserId, user1.id, None)
    val contact2 = UserContact(ExampleUserId, user3.id, Some("Carlos"))
    val contact3 = UserContact(user1.id, user4.id, Some("Dan"))
    val contact4 = UserContact(ExampleUserId, user5.id, Some("Ivan"))
    val contact5 = UserContact(ExampleUserId, user7.id, Some("Adam"))
    val contact6 = UserContact(ExampleUserId, user8.id, None)

    val initRepo = UsersRepoFake.UsersRepoState(
      users = Set(user1, user2, user3, user4, user5, user6, user7, user8),
      contacts = Set(contact1, contact2, contact3, contact4, contact5, contact6)
    )

    for {
      internalTopic   <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo       <- Ref.make(initRepo)
      collectionsRepo <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result          <-
        UsersService.listContactsAs(ExampleUserId, 30, 3, None).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
    } yield assert(result)(
      equalTo(
        PaginationEnvelope(
          List(
            (contact1, user1),
            (contact6, user8)
          ),
          5
        )
      )
    )
  }

  private val listContactsWithNameFilter = testM("should list contacts with name filter") {
    val user1 = ExampleUser.copy(id = ExampleFuuid1, nickName = "Alice")
    val user2 = ExampleUser.copy(id = ExampleFuuid2, nickName = "Bob")
    val user3 = ExampleUser.copy(id = ExampleFuuid3, nickName = "Carol")
    val user4 = ExampleUser.copy(id = ExampleFuuid4, nickName = "Dave")
    val user5 = ExampleUser.copy(id = ExampleFuuid5, nickName = "Eve")
    val user6 = ExampleUser.copy(id = ExampleFuuid6, nickName = "Frank")
    val user7 = ExampleUser.copy(id = ExampleFuuid7, nickName = "Teddy")
    val user8 = ExampleUser.copy(id = ExampleFuuid8, nickName = "Heidi")

    val contact1 = UserContact(ExampleUserId, user1.id, None)
    val contact2 = UserContact(ExampleUserId, user3.id, Some("Carlos"))
    val contact3 = UserContact(user1.id, user4.id, Some("Dan"))
    val contact4 = UserContact(ExampleUserId, user5.id, Some("Freddy"))
    val contact5 = UserContact(ExampleUserId, user7.id, Some("Adam"))
    val contact6 = UserContact(ExampleUserId, user8.id, None)

    val initRepo = UsersRepoFake.UsersRepoState(
      users = Set(user1, user2, user3, user4, user5, user6, user7, user8),
      contacts = Set(contact1, contact2, contact3, contact4, contact5, contact6)
    )

    for {
      internalTopic   <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo       <- Ref.make(initRepo)
      collectionsRepo <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result          <- UsersService
                           .listContactsAs(ExampleUserId, 30, 0, Some("eDd"))
                           .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
    } yield assert(result)(
      equalTo(
        PaginationEnvelope(
          List(
            (contact5, user7),
            (contact4, user5)
          ),
          5
        )
      )
    )
  }

  private val updateContact = testM("should update contact") {
    val user2 = ExampleUser.copy(id = ExampleContact.contactId, emailHash = "foo")
    val usersRepoState =
      UsersRepoFake.UsersRepoState(
        users = Set(ExampleUser, user2),
        contacts = Set(ExampleContact)
      )

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(usersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      contact               <- UsersService
                                 .patchContactAs(ExampleUserId, ExampleContact.contactId, editContactDto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(contact)(equalTo((ExampleContact.copy(alias = Some("newAlias")), user2))) &&
      assert(finalUsersRepoState.contacts)(hasSameElements(Set(ExampleContact.copy(alias = Some("newAlias"))))) &&
      assert(finalUsersRepoState.users)(equalTo(usersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(equalTo(usersRepoState.auth)) &&
      assert(finalUsersRepoState.tokens)(equalTo(usersRepoState.tokens)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updateContactNoUpdates = testM("should not update contact when no updates provided") {
    val dto = PatchContactReq(None)

    val user2 = ExampleUser.copy(id = ExampleContact.contactId, emailHash = "foo")
    val usersRepoState =
      UsersRepoFake.UsersRepoState(
        users = Set(ExampleUser, user2),
        contacts = Set(ExampleContact)
      )

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(usersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      contact               <- UsersService
                                 .patchContactAs(ExampleUserId, ExampleContact.contactId, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(contact)(isLeft(equalTo(NoUpdates("contact", ExampleUserId, dto)))) &&
      assert(finalUsersRepoState)(equalTo(usersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updateContactNotFound = testM("should not update contact if contact not found") {
    val user2 = ExampleUser.copy(id = ExampleContact.contactId, emailHash = "foo")
    val usersRepoState =
      UsersRepoFake.UsersRepoState(
        users = Set(ExampleUser, user2),
        contacts = Set()
      )

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(usersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      contact               <- UsersService
                                 .patchContactAs(ExampleUserId, ExampleContact.contactId, editContactDto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(contact)(isLeft(equalTo(ContactNotFound(ExampleUserId, ExampleContact.contactId)))) &&
      assert(finalUsersRepoState)(equalTo(usersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updateContactAliasExists = testM("should not update contact if alias is already in use") {
    val user2 = ExampleUser.copy(id = ExampleContact.contactId, emailHash = "foo")
    val usersRepoState =
      UsersRepoFake.UsersRepoState(
        users = Set(ExampleUser, user2),
        contacts = Set(ExampleContact, UserContact(ExampleUser.id, ExampleFuuid1, Some("newAlias")))
      )

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(usersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      contact               <- UsersService
                                 .patchContactAs(ExampleUserId, ExampleContact.contactId, editContactDto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(contact)(isLeft(equalTo(ContactAliasAlreadyExists(ExampleUser.id, "newAlias", ExampleFuuid1)))) &&
      assert(finalUsersRepoState)(equalTo(usersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updateUserData = testM("should update user data") {
    val dto = PatchUserDataReq(nickName = Some(Nickname("Other name")), language = None)

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser))
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      user                  <- UsersService.patchUserData(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(user)(equalTo(ExampleUser.copy(nickName = "Other name"))) &&
      assert(finalUsersRepoState.users)(equalTo(Set(ExampleUser.copy(nickName = "Other name")))) &&
      assert(finalUsersRepoState.auth)(equalTo(initUsersRepoState.auth)) &&
      assert(finalUsersRepoState.tokens)(equalTo(initUsersRepoState.tokens)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updateUserDataWithNoUpdates = testM("should not update user data if no updates provided") {
    val dto = PatchUserDataReq(nickName = None, language = None)

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser))
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <-
        UsersService.patchUserData(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(NoUpdates("user", ExampleUserId, dto)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updateUserDataWithNickNameConflict = testM("should not update user data on nickname conflict") {
    val dto = PatchUserDataReq(nickName = Some(Nickname(ExampleUserNickName)), None)

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser))
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <-
        UsersService.patchUserData(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(NickNameAlreadyRegistered(ExampleUserNickName)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updateUserDataWithMissingUser = testM("should not update user data when user not found") {
    val dto = PatchUserDataReq(nickName = Some(Nickname("Other name")), language = None)

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState()
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <-
        UsersService.patchUserData(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(UserNotFound(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updatePassword = testM("should update user password") {
    val dto = UpdatePasswordReq(
      ExampleUserEmail,
      ExampleUserPassword,
      Password("new-secret-password"),
      keyPairDto,
      List(encryptionKeyData.copy(key = "updated-key"))
    )

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
                             keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      initialCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
                                      collectionKeys = Set(
                                        EncryptionKey(
                                          ExampleCollectionId,
                                          ExampleUserId,
                                          ExampleEncryptionKeyValue,
                                          ExampleEncryptionKeyVersion
                                        )
                                      ),
                                      collections = Set(ExampleCollection),
                                      userCollections = Set(UserCollection(ExampleCollectionId, ExampleUserId, accepted = true))
                                    )
      collectionsRepo       <- Ref.make(initialCollectionsRepoState)
      _                     <-
        UsersService.updatePasswordForUser(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(
        hasSameElements(Set(UserAuth(ExampleUserId, "bcrypt(new-secret-password)", confirmed = true, enabled = true)))
      ) &&
      assert(finalUsersRepoState.tokens)(equalTo(initUsersRepoState.tokens)) &&
      assert(finalUsersRepoState.keyPairs)(
        hasSameElements(Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, ExamplePublicKey, ExamplePrivateKey)))
      ) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(
        equalTo(
          initialCollectionsRepoState.copy(collectionKeys =
            Set(EncryptionKey(ExampleCollectionId, ExampleUserId, "updated-key", ExampleEncryptionKeyVersion))
          )
        )
      )
  }

  private val updatePasswordWithMissingEmail = testM("should not update user password if emails is missing") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"), keyPairDto, List(encryptionKeyData))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .updatePasswordForUser(ExampleUserId, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(EmailNotFound(ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updatePasswordWithInvalidCredentials = testM("should not update user password if current password is invalid") {
    val dto = UpdatePasswordReq(ExampleUserEmail, "invalid-password", Password("new-secret-password"), keyPairDto, List(encryptionKeyData))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
                             keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .updatePasswordForUser(ExampleUserId, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(InvalidPassword(ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updatePasswordWithInvalidEmail = testM("should not update user password if email address does not match") {
    val dto = UpdatePasswordReq(
      Email("other@example.org"),
      ExampleUserPassword,
      Password("new-secret-password"),
      keyPairDto,
      List(encryptionKeyData)
    )

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
                             keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .updatePasswordForUser(ExampleUserId, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(EmailDigestDoesNotMatch(ExampleUserId, "digest(other@example.org)", ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updatePasswordWithTheSameValue = testM("should not update user password if new password is equal previous one") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password(ExampleUserPassword), keyPairDto, List(encryptionKeyData))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
                             keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .updatePasswordForUser(ExampleUserId, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(PasswordsEqual(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updatePasswordForInactiveUser = testM("should not update password is user is inactive") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"), keyPairDto, List(encryptionKeyData))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = false)),
                             keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .updatePasswordForUser(ExampleUserId, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(UserIsNotActive(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updatePasswordForNonExistingUser = testM("should not update password if user not exists") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"), keyPairDto, List(encryptionKeyData))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState()
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .updatePasswordForUser(ExampleUserId, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(UserNotFound(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updatePasswordWithMissingEncryptionKey = testM("should not update user password if some encryption keys are missing") {
    val dto = UpdatePasswordReq(ExampleUserEmail, ExampleUserPassword, Password("new-secret-password"), keyPairDto, List(encryptionKeyData))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
                             keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
                                   collectionKeys = Set(
                                     EncryptionKey(
                                       ExampleCollectionId,
                                       ExampleUserId,
                                       ExampleEncryptionKeyValue,
                                       ExampleEncryptionKeyVersion
                                     ),
                                     EncryptionKey(ExampleFuuid1, ExampleUserId, "FooBar", ExampleFuuid2)
                                   )
                                 )
      collectionsRepo       <- Ref.make(initCollectionsRepoState)
      result                <- UsersService
                                 .updatePasswordForUser(ExampleUserId, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(SomeEncryptionKeysMissingInUpdate(ExampleUserId, Set(ExampleFuuid1))))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(initCollectionsRepoState))
  }

  private val updatePasswordWithNotOwnedCollectionKey =
    testM("should not update user password if request contains collection key not owned by user") {
      val dto = UpdatePasswordReq(
        ExampleUserEmail,
        ExampleUserPassword,
        Password("new-secret-password"),
        keyPairDto,
        List(encryptionKeyData.copy(key = "updated-key"), EncryptionKeyData(ExampleFuuid1, "some-key", ExampleFuuid2))
      )

      for {
        internalTopic   <- Ref.make[List[InternalMessage]](List.empty)
        initUsersRepoState = UsersRepoFake.UsersRepoState(
                               users = Set(ExampleUser),
                               auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
                               keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                             )
        usersRepo       <- Ref.make(initUsersRepoState)
        initialCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
                                        collectionKeys = Set(
                                          EncryptionKey(
                                            ExampleCollectionId,
                                            ExampleUserId,
                                            ExampleEncryptionKeyValue,
                                            ExampleEncryptionKeyVersion
                                          )
                                        ),
                                        collections = Set(ExampleCollection),
                                        userCollections = Set(UserCollection(ExampleCollectionId, ExampleUserId, accepted = true))
                                      )
        collectionsRepo <- Ref.make(initialCollectionsRepoState)
        result          <- UsersService
                             .updatePasswordForUser(ExampleUserId, dto)
                             .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                             .either
        sentMessages    <- internalTopic.get
      } yield assert(result)(
        isLeft(
          equalTo(AuthorizationError(OperationNotPermitted(SetEncryptionKey(Subject(ExampleUserId), ExampleFuuid1, ExampleUserId))))
        )
      ) &&
        assert(sentMessages)(isEmpty)
    }

  private val updatePasswordWithInvalidKeyVersion =
    testM("should not update user password if encryption key for one of collections is different than current one") {
      val dto = UpdatePasswordReq(
        ExampleUserEmail,
        ExampleUserPassword,
        Password("new-secret-password"),
        keyPairDto,
        List(encryptionKeyData.copy(version = ExampleFuuid1))
      )

      for {
        internalTopic   <- Ref.make[List[InternalMessage]](List.empty)
        initUsersRepoState = UsersRepoFake.UsersRepoState(
                               users = Set(ExampleUser),
                               auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
                               keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                             )
        usersRepo       <- Ref.make(initUsersRepoState)
        initialCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
                                        collectionKeys = Set(
                                          EncryptionKey(
                                            ExampleCollectionId,
                                            ExampleUserId,
                                            ExampleEncryptionKeyValue,
                                            ExampleEncryptionKeyVersion
                                          )
                                        ),
                                        collections = Set(ExampleCollection),
                                        userCollections = Set(UserCollection(ExampleCollectionId, ExampleUserId, accepted = true))
                                      )
        collectionsRepo <- Ref.make(initialCollectionsRepoState)
        result          <- UsersService
                             .updatePasswordForUser(ExampleUserId, dto)
                             .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                             .either
        sentMessages    <- internalTopic.get
      } yield assert(result)(
        isLeft(equalTo(EncryptionKeyVersionMismatch(ExampleCollectionId, ExampleFuuid1, ExampleEncryptionKeyVersion)))
      ) &&
        assert(sentMessages)(isEmpty)
    }

  private val passwordResetRequest = testM("should generate reset password token") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      token                 <- UsersService
                                 .requestPasswordResetForUser(ExampleUserEmail)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
      expectedToken = TemporaryToken("000102030405060708090A0B0C0D0E0F1011121314151617", ExampleUserId, TokenType.PasswordReset)
    } yield assert(token)(equalTo(expectedToken)) &&
      assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(equalTo(initUsersRepoState.auth)) &&
      assert(finalUsersRepoState.tokens)(hasSameElements(Set(expectedToken))) &&
      assert(sentMessages)(
        equalTo(List(InternalMessage.PasswordResetRequested(ExampleUser, ExampleUserEmail, expectedToken.token, Some(Context.Empty))))
      ) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val passwordResetRequestForNonExistingEmail = testM("should not generate reset password token if email not exists") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState()
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .requestPasswordResetForUser(ExampleUserEmail)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(EmailNotFound(ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val passwordResetRequestForInactiveUser = testM("should not generate reset password token if user is inactive") {
    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = false))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .requestPasswordResetForUser(ExampleUserEmail)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(UserIsNotActive(ExampleUserId)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val passwordResetWithToken = testM("should reset password") {
    val dto = PasswordResetReq(Password("new-secret-password"), ExampleUserEmail, keyPairDto)

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
                             tokens = Set(
                               TemporaryToken("abc", ExampleUserId, TokenType.PasswordReset),
                               TemporaryToken("xyz", ExampleUserId, TokenType.PasswordReset)
                             ),
                             keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionKey1 = EncryptionKey(ExampleFuuid1, ExampleFuuid2, "aaa", ExampleFuuid3)
      collectionKey2 = EncryptionKey(ExampleFuuid1, ExampleUserId, "bbb", ExampleFuuid3)
      collectionKey3 = EncryptionKey(ExampleFuuid4, ExampleUserId, "ccc", ExampleFuuid5)
      collectionKey4 = EncryptionKey(ExampleFuuid4, ExampleFuuid6, "aaa", ExampleFuuid7)
      initCollectionsRepoState = CollectionsRepoFake.CollectionsRepoState(
                                   collectionKeys = Set(collectionKey1, collectionKey2, collectionKey3, collectionKey4)
                                 )
      collectionsRepo       <- Ref.make(initCollectionsRepoState)
      _                     <- UsersService.passwordResetUsingToken("abc", dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(
        hasSameElements(Set(UserAuth(ExampleUserId, "bcrypt(new-secret-password)", confirmed = true, enabled = true)))
      ) &&
      assert(finalUsersRepoState.tokens)(isEmpty) &&
      assert(finalUsersRepoState.keyPairs)(
        hasSameElements(Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, ExamplePublicKey, ExamplePrivateKey)))
      ) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(initCollectionsRepoState.copy(collectionKeys = Set(collectionKey1, collectionKey4))))
  }

  private val passwordResetWithTokenForInactiveUser = testM("should not reset password if user is not active") {
    val dto = PasswordResetReq(Password("new-secret-password"), ExampleUserEmail, keyPairDto)

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = false)),
                             tokens = Set(
                               TemporaryToken("abc", ExampleUserId, TokenType.PasswordReset),
                               TemporaryToken("xyz", ExampleUserId, TokenType.PasswordReset)
                             ),
                             keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <-
        UsersService.passwordResetUsingToken("abc", dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(UserIsNotActive(ExampleUserId)))) &&
      assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(equalTo(initUsersRepoState.auth)) &&
      assert(finalUsersRepoState.tokens)(equalTo(initUsersRepoState.tokens)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val passwordResetWithTokenForInvalidEmail = testM("should not reset password if email address does not match") {
    val dto = PasswordResetReq(Password("new-secret-password"), Email("other@example.org"), keyPairDto)

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      initUsersRepoState = UsersRepoFake.UsersRepoState(
                             users = Set(ExampleUser),
                             auth = Set(UserAuth(ExampleUserId, ExampleUserPasswordHash, confirmed = true, enabled = true)),
                             tokens = Set(
                               TemporaryToken("abc", ExampleUserId, TokenType.PasswordReset),
                               TemporaryToken("xyz", ExampleUserId, TokenType.PasswordReset)
                             ),
                             keyPairs = Set(KeyPair(ExampleFuuid4, ExampleUserId, KeyAlgorithm.Rsa, "pub-key", "priv-key"))
                           )
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <-
        UsersService.passwordResetUsingToken("abc", dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo)).either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(EmailDigestDoesNotMatch(ExampleUserId, "digest(other@example.org)", ExampleUserEmailDigest)))) &&
      assert(finalUsersRepoState.users)(equalTo(initUsersRepoState.users)) &&
      assert(finalUsersRepoState.auth)(equalTo(initUsersRepoState.auth)) &&
      assert(finalUsersRepoState.tokens)(equalTo(initUsersRepoState.tokens)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val createApiKey = testM("should create new API key") {
    val dto = NewPersonalApiKeyReq(ApiKeyDescription("My Key"), None)
    val initUsersRepoState = UsersRepoFake.UsersRepoState()
    val expectedKey =
      ApiKey(
        FirstRandomFuuid,
        "000102030405060708090a0b0c0d0e0f1011121314151617",
        ExampleUserId,
        ApiKeyType.Personal,
        "My Key",
        enabled = true,
        None,
        UsersRepoFake.RepoTimeNow,
        UsersRepoFake.RepoTimeNow
      )

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      key                   <-
        UsersService.createApiKeyForUser(ExampleUserId, dto).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(key)(equalTo(expectedKey)) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState.copy(apiKeys = initUsersRepoState.apiKeys + expectedKey))) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val getApiKeys = testM("should get API key") {
    val otherUsersKey = ExampleApiKey.copy(id = ExampleFuuid2, key = "XYZ", userId = ExampleFuuid3)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey, otherUsersKey))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      keys                  <- UsersService
                                 .getApiKeysOf(ExampleUserId, ApiKeyType.Personal)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(keys)(hasSameElements(List(ExampleApiKey))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val deleteApiKey = testM("should delete API key") {
    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      _                     <- UsersService
                                 .deleteApiKeyAs(ExampleUserId, ExampleApiKeyId)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(finalUsersRepoState)(equalTo(initUsersRepoState.copy(apiKeys = Set.empty))) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val deleteNonExistingApiKey = testM("should not delete API key if key not found") {
    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey.copy(id = ExampleFuuid3)))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .deleteApiKeyAs(ExampleUserId, ExampleFuuid1)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(ApiKeyNotFound(ExampleFuuid1)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val deleteApiKeyOwnedByAnotherUser = testM("should not delete API key which belongs to another user") {
    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey.copy(userId = ExampleFuuid3)))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .deleteApiKeyAs(ExampleUserId, ExampleApiKeyId)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(ApiKeyBelongsToAnotherUser(ExampleApiKeyId, ExampleUserId, ExampleFuuid3)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updateApiKey = testM("should update API key") {
    val dto = UpdateApiKeyDataReq(None, Some(false), Some(OptionalValue(Some(Instant.EPOCH.plusSeconds(60)))))

    val expectedKey = ExampleApiKey.copy(enabled = false, validTo = Some(Instant.EPOCH.plusSeconds(60)))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .updateApiKeyAs(ExampleUserId, ExampleApiKeyId, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(equalTo(expectedKey)) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState.copy(apiKeys = Set(expectedKey)))) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updateApiKeyWithEmptyUpdatesSet = testM("should not update API key if nothing has changed") {
    val dto = UpdateApiKeyDataReq(None, None, None)

    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .updateApiKeyAs(ExampleUserId, ExampleFuuid1, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(NoUpdates("API key", ExampleFuuid1, dto)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updateNonExistingApiKey = testM("should not update API key if key not found") {
    val dto = UpdateApiKeyDataReq(None, Some(false), Some(OptionalValue(Some(Instant.EPOCH.plusSeconds(60)))))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .updateApiKeyAs(ExampleUserId, ExampleFuuid3, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(ApiKeyNotFound(ExampleFuuid3)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val updateApiKeyOwnedByAnotherUser = testM("should not update API key owned by another user") {
    val dto = UpdateApiKeyDataReq(None, Some(false), Some(OptionalValue(Some(Instant.EPOCH.plusSeconds(60)))))

    val initUsersRepoState = UsersRepoFake.UsersRepoState(apiKeys = Set(ExampleApiKey.copy(userId = ExampleFuuid3)))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService
                                 .updateApiKeyAs(ExampleUserId, ExampleApiKeyId, dto)
                                 .provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
                                 .either
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isLeft(equalTo(ApiKeyBelongsToAnotherUser(ExampleApiKeyId, ExampleUserId, ExampleFuuid3)))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val getKeyPair = testM("should get keypair for user") {
    val initUsersRepoState = UsersRepoFake.UsersRepoState(keyPairs = Set(ExampleKeyPair))

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService.getKeyPairOf(ExampleUserId).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isSome(equalTo(ExampleKeyPair))) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

  private val getKeyPairIfNotExists = testM("should try to get non existing keypair") {
    val initUsersRepoState = UsersRepoFake.UsersRepoState(keyPairs = Set.empty)

    for {
      internalTopic         <- Ref.make[List[InternalMessage]](List.empty)
      usersRepo             <- Ref.make(initUsersRepoState)
      collectionsRepo       <- Ref.make(CollectionsRepoFake.CollectionsRepoState())
      result                <- UsersService.getKeyPairOf(ExampleUserId).provideLayer(createUsersService(usersRepo, internalTopic, collectionsRepo))
      finalUsersRepoState   <- usersRepo.get
      sentMessages          <- internalTopic.get
      finalCollectionsState <- collectionsRepo.get
    } yield assert(result)(isNone) &&
      assert(finalUsersRepoState)(equalTo(initUsersRepoState)) &&
      assert(sentMessages)(isEmpty) &&
      assert(finalCollectionsState)(equalTo(CollectionsRepoFake.CollectionsRepoState()))
  }

}
