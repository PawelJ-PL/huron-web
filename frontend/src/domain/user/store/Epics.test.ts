import {
    exampleCollectionId,
    exampleEncryptedCollectionKey,
    exampleEncryptionKey,
    exampleEncryptionKeyVersion,
} from "./../../../testutils/constants/collection"
import { NotLoggedIn, NoUpdatesProvides } from "./../../../application/api/ApiError"
import { usersEpics } from "./Epics"
import {
    exampleApiKey,
    exampleEncryptedKeypair,
    exampleEncryptedPrivateKey,
    exampleHashedEmail,
    examplePublicKey,
    exampleUserEmail,
    exampleUserId,
    exampleUserNickname,
    exampleUserPassword,
} from "./../../../testutils/constants/user"
import {
    apiLogoutAction,
    changePasswordAction,
    computeMasterKeyAction,
    fetchAndDecryptKeyPairAction,
    fetchCurrentUserAction,
    localLogoutAction,
    loginAction,
    registerNewUserAction,
    resetPasswordAction,
    updateApiKeyAction,
    updateUserDataAction,
} from "./Actions"
import { verifyAsyncEpic, verifyEpic } from "./../../../testutils/epicsUtils"
import { AppState } from "../../../application/store"
import UsersApi from "../api/UsersApi"
import CryptoApi from "../../../application/cryptography/api/CryptoApi"
import actionCreatorFactory from "typescript-fsa"
import { Unauthorized } from "../../../application/api/ApiError"
import CollectionsApi from "../../collection/api/CollectionsApi"

const defaultState = {} as AppState

const asyncAction = actionCreatorFactory("EPIC_TEST").async<{ foo: string; bar: number }, string, Error>("TRIGGER")

const exampleDerivedLoginPassword = `derivedKey(derivedKey(${exampleUserPassword}, ${exampleHashedEmail}), ${exampleHashedEmail})`

describe("User epics", () => {
    beforeEach(() => {
        jest.spyOn(CryptoApi, "deriveKey").mockImplementation((password, salt) =>
            Promise.resolve(`derivedKey(${password}, ${salt})`)
        )

        jest.spyOn(CryptoApi, "encrypt").mockImplementation((input, keyHex) =>
            Promise.resolve(`encrypted(${input}, ${keyHex})`)
        )

        jest.spyOn(CryptoApi, "decrypt").mockImplementation((encryptedInput: string, keyHex: string) =>
            Promise.resolve(`decrypted(${encryptedInput}, ${keyHex})`)
        )

        jest.spyOn(CryptoApi, "deriveKey").mockImplementation((password, salt) =>
            Promise.resolve(`derivedKey(${password}, ${salt})`)
        )

        jest.spyOn(CryptoApi, "generateKeyPair").mockImplementation((length) =>
            Promise.resolve({ privateKey: `new-private-key: ${length}`, publicKey: "new-public-key" })
        )

        jest.spyOn(CryptoApi, "asymmetricDecrypt").mockImplementation((encryptedInput: string, privateKeyPem: string) =>
            Promise.resolve(`decryptedAsym(${encryptedInput} : ${privateKeyPem})`)
        )

        jest.spyOn(CryptoApi, "asymmetricEncrypt").mockImplementation((input: string, publicKeyPem: string) =>
            Promise.resolve(`encryptedAsym(${input} : ${publicKeyPem})`)
        )
    })

    afterEach(() => {
        jest.restoreAllMocks()
    })

    it("should trigger login with derived password", async () => {
        const loginSpy = jest.spyOn(UsersApi, "login").mockResolvedValue(null)
        const trigger = loginAction.started({ email: exampleUserEmail, password: exampleUserPassword })
        await verifyAsyncEpic(
            trigger,
            usersEpics,
            defaultState,
            loginAction.done({ params: { email: exampleUserEmail, password: exampleUserPassword }, result: null })
        )
        expect(loginSpy).toHaveBeenCalledWith(exampleUserEmail, exampleDerivedLoginPassword)
    })

    it("should trigger not logged in error on unauthorized error in any action if needLogout set to true", () => {
        const trigger = asyncAction.failed({ params: { foo: "a", bar: 1 }, error: new Unauthorized(true) })
        const expectedAction = fetchCurrentUserAction.failed({ params: void 0, error: new NotLoggedIn() })
        verifyEpic(trigger, usersEpics, defaultState, { marbles: "-a", values: { a: expectedAction } })
    })

    it("should do nothing on unauthorized error in any action if needLogout set to false", () => {
        const trigger = asyncAction.failed({ params: { foo: "a", bar: 1 }, error: new Unauthorized(false) })
        verifyEpic(trigger, usersEpics, defaultState, { marbles: "---" })
    })

    it("should do nothing on unauthorized error in any action if needLogout not set", () => {
        const trigger = asyncAction.failed({ params: { foo: "a", bar: 1 }, error: new Unauthorized() })
        verifyEpic(trigger, usersEpics, defaultState, { marbles: "---" })
    })

    it("should trigger signup with derived password", async () => {
        const loginSpy = jest.spyOn(UsersApi, "registerUser").mockResolvedValue(void 0)
        const trigger = registerNewUserAction.started({
            email: exampleUserEmail,
            password: exampleUserPassword,
            nickname: exampleUserNickname,
            language: "Pl",
        })
        await verifyAsyncEpic(
            trigger,
            usersEpics,
            defaultState,
            registerNewUserAction.done({
                params: {
                    email: exampleUserEmail,
                    password: exampleUserPassword,
                    nickname: exampleUserNickname,
                    language: "Pl",
                },
                result: void 0,
            })
        )
        expect(loginSpy).toHaveBeenCalledWith(
            exampleUserNickname,
            exampleUserEmail,
            exampleDerivedLoginPassword,
            {
                algorithm: "Rsa",
                publicKey: "new-public-key",
                encryptedPrivateKey: `encrypted(new-private-key: 4096, derivedKey(secret-password, ${exampleHashedEmail}))`,
            },
            "Pl"
        )
    })

    it("should set master password after successful login", () => {
        const resultData = {
            csrfToken: "ABC",
            userData: {
                id: exampleUserId,
                nickName: exampleUserNickname,
                language: "Pl",
                emailHash: exampleHashedEmail,
            },
        }
        const trigger = loginAction.done({
            params: { password: exampleUserPassword, email: exampleUserEmail },
            result: resultData,
        })
        return verifyAsyncEpic(
            trigger,
            usersEpics,
            defaultState,
            computeMasterKeyAction.done({
                params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
                result: `derivedKey(${exampleUserPassword}, ${exampleHashedEmail})`,
            })
        )
    })

    it("should set failed compute master key action successful login when derivation failed", () => {
        jest.restoreAllMocks()
        jest.spyOn(CryptoApi, "deriveKey").mockImplementation(() => Promise.reject(new Error("Unknown error")))
        const resultData = {
            csrfToken: "ABC",
            userData: {
                id: exampleUserId,
                nickName: exampleUserNickname,
                language: "Pl",
                emailHash: exampleHashedEmail,
            },
        }
        const trigger = loginAction.done({
            params: { password: exampleUserPassword, email: exampleUserEmail },
            result: resultData,
        })
        return verifyAsyncEpic(
            trigger,
            usersEpics,
            defaultState,
            computeMasterKeyAction.failed({
                params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
                error: new Error("Unknown error"),
            })
        )
    })

    it("should do not compute master password if login failed", () => {
        const trigger = loginAction.done({
            params: { password: exampleUserPassword, email: exampleUserEmail },
            result: null,
        })
        verifyEpic(trigger, usersEpics, defaultState, { marbles: "---" })
    })

    it("should trigger password reset with derived password", async () => {
        const passwordResetSpy = jest.spyOn(UsersApi, "resetPassword").mockResolvedValue(true)
        const trigger = resetPasswordAction.started({
            email: exampleUserEmail,
            resetToken: "A-B-C",
            newPassword: "updated-password",
        })
        await verifyAsyncEpic(
            trigger,
            usersEpics,
            defaultState,
            resetPasswordAction.done({
                params: { email: exampleUserEmail, resetToken: "A-B-C", newPassword: "updated-password" },
                result: true,
            })
        )
        expect(passwordResetSpy).toHaveBeenCalledWith(
            "A-B-C",
            `derivedKey(derivedKey(${"updated-password"}, ${exampleHashedEmail}), ${exampleHashedEmail})`,
            {
                algorithm: "Rsa",
                publicKey: "new-public-key",
                encryptedPrivateKey: `encrypted(new-private-key: 4096, derivedKey(updated-password, ${exampleHashedEmail}))`,
            },
            exampleUserEmail
        )
    })

    it("should trigger user data update", async () => {
        jest.spyOn(UsersApi, "updateProfile").mockResolvedValue({
            id: exampleUserId,
            nickName: "newNickname",
            language: "Pl",
            emailHash: exampleHashedEmail,
        })
        const trigger = updateUserDataAction.started({ nickName: "newNickname" })
        await verifyAsyncEpic(
            trigger,
            usersEpics,
            defaultState,
            updateUserDataAction.done({
                params: { nickName: "newNickname" },
                result: { id: exampleUserId, nickName: "newNickname", language: "Pl", emailHash: exampleHashedEmail },
            })
        )
    })

    it("should trigger failed action when update user data without any new data", async () => {
        const trigger = updateUserDataAction.started({ nickName: undefined })
        await verifyAsyncEpic(
            trigger,
            usersEpics,
            defaultState,
            updateUserDataAction.failed({
                params: { nickName: undefined },
                error: new NoUpdatesProvides(),
            })
        )
    })

    it("should trigger API key update", async () => {
        jest.spyOn(UsersApi, "updateApiKey").mockResolvedValue(exampleApiKey)
        const trigger = updateApiKeyAction.started({
            keyId: exampleApiKey.id,
            data: { description: "new-description" },
        })
        await verifyAsyncEpic(
            trigger,
            usersEpics,
            defaultState,
            updateApiKeyAction.done({
                params: { keyId: exampleApiKey.id, data: { description: "new-description" } },
                result: exampleApiKey,
            })
        )
    })

    it("should trigger failed action when update API key without any new data", async () => {
        const trigger = updateApiKeyAction.started({ keyId: exampleApiKey.id, data: {} })
        await verifyAsyncEpic(
            trigger,
            usersEpics,
            defaultState,
            updateApiKeyAction.failed({
                params: { keyId: exampleApiKey.id, data: {} },
                error: new NoUpdatesProvides(),
            })
        )
    })

    it("should trigger local logout after successful API logout", () => {
        const trigger = apiLogoutAction.done({ params: void 0, result: void 0 })
        verifyEpic(trigger, usersEpics, defaultState, { marbles: "-a", values: { a: localLogoutAction() } })
    })

    it("should trigger password change with derived passwords", async () => {
        const changePasswordSpy = jest.spyOn(UsersApi, "changePassword").mockResolvedValue(void 0)
        jest.spyOn(CollectionsApi, "fetchAllEncryptionKeys").mockResolvedValue([exampleEncryptionKey])
        jest.spyOn(UsersApi, "fetchKeyPair").mockResolvedValue(exampleEncryptedKeypair)

        const trigger = changePasswordAction.started({
            email: exampleUserEmail,
            currentPassword: exampleUserPassword,
            newPassword: "new-password",
        })
        await verifyAsyncEpic(
            trigger,
            usersEpics,
            defaultState,
            changePasswordAction.done({
                params: { email: exampleUserEmail, currentPassword: exampleUserPassword, newPassword: "new-password" },
                result: void 0,
            })
        )
        expect(changePasswordSpy).toHaveBeenCalledWith({
            currentPassword: exampleDerivedLoginPassword,
            email: exampleUserEmail,
            keyPair: {
                algorithm: "Rsa",
                publicKey: "new-public-key",
                encryptedPrivateKey: `encrypted(new-private-key: 4096, derivedKey(new-password, ${exampleHashedEmail}))`,
            },
            newPassword: `derivedKey(derivedKey(${"new-password"}, ${exampleHashedEmail}), ${exampleHashedEmail})`,
            collectionEncryptionKeys: [
                {
                    collectionId: exampleCollectionId,
                    version: exampleEncryptionKeyVersion,
                    key: `encryptedAsym(decryptedAsym(${exampleEncryptedCollectionKey} : decrypted(${exampleEncryptedPrivateKey}, derivedKey(secret-password, ${exampleHashedEmail}))) : new-public-key)`,
                },
            ],
        })
    })

    it("should trigger key pair fetch and decrypt", async () => {
        jest.spyOn(UsersApi, "fetchKeyPair").mockResolvedValue({
            algorithm: "Rsa",
            publicKey: examplePublicKey,
            encryptedPrivateKey: exampleEncryptedPrivateKey,
        })
        const keyHex = "12aabbccddff"
        const trigger = fetchAndDecryptKeyPairAction.started(keyHex)
        await verifyAsyncEpic(
            trigger,
            usersEpics,
            defaultState,
            fetchAndDecryptKeyPairAction.done({
                params: keyHex,
                result: {
                    privateKey: `decrypted(${exampleEncryptedPrivateKey}, ${keyHex})`,
                    publicKey: examplePublicKey,
                },
            })
        )
    })

    it("should trigger key pair fetch and decrypt on master key set", () => {
        const masterKey = "12aabbccddff"
        const trigger = computeMasterKeyAction.done({
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
            result: masterKey,
        })
        verifyEpic(trigger, usersEpics, defaultState, {
            marbles: "-a",
            values: { a: fetchAndDecryptKeyPairAction.started(masterKey) },
        })
    })
})
