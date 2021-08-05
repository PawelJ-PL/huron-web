package com.github.huronapp.api.domain.users

import com.github.huronapp.api.auth.authorization.types.Subject
import com.github.huronapp.api.auth.authorization.{AuthorizationKernel, OperationNotPermitted, SetEncryptionKey}
import com.github.huronapp.api.constants.{Collections, Config, MiscConstants, Users}
import com.github.huronapp.api.domain.collections.EncryptionKey
import com.github.huronapp.api.domain.collections.dto.EncryptionKeyData
import com.github.huronapp.api.domain.users.UsersService.UsersService
import com.github.huronapp.api.domain.users.dto.fields.{ApiKeyDescription, Nickname, Password, PrivateKey, PublicKey, KeyPair => KeyPairDto}
import com.github.huronapp.api.domain.users.dto.{
  NewPersonalApiKeyReq,
  NewUserReq,
  PasswordResetReq,
  PatchUserDataReq,
  UpdateApiKeyDataReq,
  UpdatePasswordReq
}
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
import io.github.gaelrenoux.tranzactio.doobie.Database
import kamon.context.Context
import zio.blocking.Blocking
import zio.{Ref, ZLayer}
import zio.logging.slf4j.Slf4jLogger
import zio.test.Assertion.{equalTo, hasSameElements, isEmpty, isLeft, isNone, isSome}
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

import java.time.Instant

object UserServiceSpec extends DefaultRunnableSpec with Users with Config with MiscConstants with Collections {

  private val logger = Slf4jLogger.make((_, str) => str)

  private def createUsersService(
    usersRepoRef: Ref[UsersRepoFake.UsersRepoState],
    internalTopic: Ref[List[InternalMessage]],
    collectionsRepoRef: Ref[CollectionsRepoFake.CollectionsRepoState]
  ): ZLayer[Blocking, Nothing, UsersService] = {
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
      confirmSignUp,
      confirmSignUpWithInvalidToken,
      confirmAlreadyConfirmedRegistration,
      verifyCredentials,
      verifyCredentialsForNonExistingEmail,
      verifyCredentialsForInvalidPassword,
      verifyCredentialsForInactiveUser,
      getUserData,
      getUserDataForNonExistingUser,
      updateUserData,
      updateUserDataWithNoUpdates,
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

  private val encryptionKeyData = EncryptionKeyData(ExampleCollectionId, ExampleEncryptionKey, ExampleEncryptionKeyVersion)

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
      initUsersRepoState = UsersRepoFake.UsersRepoState(users = Set(ExampleUser.copy(emailHash = "digest(alice@example.org)")))
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
                                        EncryptionKey(ExampleCollectionId, ExampleUserId, ExampleEncryptionKey, ExampleEncryptionKeyVersion)
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
                                     EncryptionKey(ExampleCollectionId, ExampleUserId, ExampleEncryptionKey, ExampleEncryptionKeyVersion),
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
                                            ExampleEncryptionKey,
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
                                            ExampleEncryptionKey,
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
