import { EncryptionKey } from "./../../collection/types/EncryptionKey"
import { NotLoggedIn } from "./../../../application/api/ApiError"
import { ApiKeyDescription } from "./../types/ApiKey"
import { UserData } from "./../types/UserData"
import { EMPTY, from } from "rxjs"
import { filter, map, catchError, mergeMap } from "rxjs/operators"
import { ApiKeyUpdateData, UserDataWithToken } from "./../api/UsersApi"
import { Action, AnyAction } from "redux"
import { combineEpics, Epic } from "redux-observable"
import { createEpic } from "./../../../application/store/async/AsyncActionEpic"
import {
    activateAccountAction,
    apiLogoutAction,
    changePasswordAction,
    ChangePasswordInputData,
    computeMasterKeyAction,
    createApiKeyAction,
    deleteApiKeyAction,
    fetchAndDecryptKeyPairAction,
    fetchApiKeysAction,
    fetchCurrentUserAction,
    localLogoutAction,
    loginAction,
    refreshUserDataAction,
    registerNewUserAction,
    RegistrationParams,
    requestPasswordResetAction,
    resetPasswordAction,
    ResetPasswordParams,
    updateApiKeyAction,
    updateUserDataAction,
} from "./Actions"
import UsersApi from "../api/UsersApi"
import { AppState } from "../../../application/store"
import CryptoApi from "../../../application/cryptography/api/CryptoApi"
import * as forge from "node-forge"
import { NoUpdatesProvides, Unauthorized } from "../../../application/api/ApiError"
import { validateNonEmptyData } from "../../../application/api/helpers"
import CollectionsApi from "../../collection/api/CollectionsApi"
import { KeyPair } from "../../../application/cryptography/types/KeyPair"

const PRIVATE_KEY_LENGTH = 4096

const hashEmail = (email: string) => {
    const messageDigest = forge.md.sha256.create()
    messageDigest.update(email.trim().toLocaleLowerCase())
    return messageDigest.digest().toHex()
}

const generateLoginPassword = async (userPassword: string, email: string) => {
    const hashedEmail = hashEmail(email)
    const masterKey = await CryptoApi.deriveKey(userPassword, hashedEmail)
    const loginPassword = await CryptoApi.deriveKey(masterKey, hashedEmail)
    return [masterKey, loginPassword]
}

const userLoginEpic = createEpic<{ email: string; password: string }, UserDataWithToken | null, Error>(
    loginAction,
    (params) =>
        generateLoginPassword(params.password, params.email).then(([_, loginKey]) =>
            UsersApi.login(params.email, loginKey)
        )
)

const currentUserDataEpic = createEpic<void, UserDataWithToken, Error>(fetchCurrentUserAction, () =>
    UsersApi.fetchCurrentUserData()
)

const setNotLoggedInOnUnauthorizedEpic: Epic<AnyAction, AnyAction, AppState> = (action$) =>
    action$.pipe(
        filter((a) => a.payload?.error instanceof Unauthorized && a.payload?.error?.needLogout === true),
        map(() => fetchCurrentUserAction.failed({ params: void 0, error: new NotLoggedIn() }))
    )

const registerUserEpic = createEpic<RegistrationParams, void, Error>(registerNewUserAction, async (params) => {
    const [masterKey, loginPassword] = await generateLoginPassword(params.password, params.email)
    const keyPair = await CryptoApi.generateKeyPair(PRIVATE_KEY_LENGTH)
    const encryptedPrivateKey = await CryptoApi.encrypt(keyPair.privateKey, masterKey)
    return await UsersApi.registerUser(
        params.nickname,
        params.email,
        loginPassword,
        { algorithm: "Rsa", publicKey: keyPair.publicKey, encryptedPrivateKey },
        params.language
    )
})

const activateUserEpic = createEpic<string, boolean, Error>(activateAccountAction, (params) =>
    UsersApi.activateAccount(params)
)

const setMasterPassOnLoginEpic: Epic<AnyAction, AnyAction, AppState> = (action$) =>
    action$.pipe(
        filter(loginAction.done.match),
        mergeMap((action) => {
            if (!action.payload.result) {
                return EMPTY
            } else {
                return from(
                    CryptoApi.deriveKey(action.payload.params.password, hashEmail(action.payload.params.email))
                ).pipe(
                    map((result) =>
                        computeMasterKeyAction.done({
                            result: result,
                            params: {
                                password: action.payload.params.password,
                                emailHash: hashEmail(action.payload.params.email),
                            },
                        })
                    ),
                    catchError((err: Error) => [
                        computeMasterKeyAction.failed({
                            params: {
                                password: action.payload.params.password,
                                emailHash: hashEmail(action.payload.params.email),
                            },
                            error: err,
                        }),
                    ])
                )
            }
        })
    )

const requestPasswordResetEpic = createEpic<string, void, Error>(requestPasswordResetAction, (params) =>
    UsersApi.requestPasswordReset(params)
)

const resetPasswordEpic = createEpic<ResetPasswordParams, boolean, Error>(
    resetPasswordAction,
    async ({ resetToken, newPassword, email }) => {
        const [masterKey, loginPassword] = await generateLoginPassword(newPassword, email)
        const keyPair = await CryptoApi.generateKeyPair(PRIVATE_KEY_LENGTH)
        const encryptedPrivateKey = await CryptoApi.encrypt(keyPair.privateKey, masterKey)
        return await UsersApi.resetPassword(
            resetToken,
            loginPassword,
            { algorithm: "Rsa", publicKey: keyPair.publicKey, encryptedPrivateKey: encryptedPrivateKey },
            email
        )
    }
)

const updateUserDataEpic = createEpic<{ nickName?: string | null; language?: string | null }, UserData, Error>(
    updateUserDataAction,
    (params) => {
        if (Object.values(params).every((value) => value === undefined || value === null)) {
            return Promise.reject(new NoUpdatesProvides())
        }
        return UsersApi.updateProfile(params)
    }
)

const refreshUserDataEpic = createEpic<void, UserDataWithToken, Error>(refreshUserDataAction, () =>
    UsersApi.fetchCurrentUserData()
)

const fetchApiKeysEpic = createEpic<void, ApiKeyDescription[], Error>(fetchApiKeysAction, () => UsersApi.getApiKeys())

const createApiKeyEpic = createEpic<{ description: string; validTo?: string }, ApiKeyDescription, Error>(
    createApiKeyAction,
    (params) => UsersApi.createApiKey(params.description, params.validTo)
)

const updateApiKeyEpic = createEpic<{ keyId: string; data: ApiKeyUpdateData }, ApiKeyDescription, Error>(
    updateApiKeyAction,
    (params) => {
        if (validateNonEmptyData(params.data)) {
            return UsersApi.updateApiKey(params.keyId, params.data)
        } else {
            return Promise.reject(new NoUpdatesProvides())
        }
    }
)

const deleteApiKeyEpic = createEpic<string, void, Error>(deleteApiKeyAction, (params) => UsersApi.deleteApiKey(params))

const apiLogoutEpic = createEpic<void, void, Error>(apiLogoutAction, () => UsersApi.logout())

const localLogoutOnApiLogoutEpic: Epic<AnyAction, AnyAction, AppState> = (action$) =>
    action$.pipe(
        filter(apiLogoutAction.done.match),
        map(() => localLogoutAction())
    )

const changePasswordEpic = createEpic<ChangePasswordInputData, void, Error>(changePasswordAction, async (params) => {
    const [currentMasterKey, currentPassword] = await generateLoginPassword(params.currentPassword, params.email)
    const [newMasterKey, newPassword] = await generateLoginPassword(params.newPassword, params.email)
    const keyPair = await CryptoApi.generateKeyPair(PRIVATE_KEY_LENGTH)
    const encryptedPrivateKey = await CryptoApi.encrypt(keyPair.privateKey, newMasterKey)
    const currentEncryptionKeys = await CollectionsApi.fetchAllEncryptionKeys()
    const currentKeyPair = await UsersApi.fetchKeyPair()
    const currentPrivateKey = await CryptoApi.decrypt(currentKeyPair.encryptedPrivateKey, currentMasterKey)

    const updatedKeys = await Promise.all(
        currentEncryptionKeys.map((key) => recryptSingleKey(key, currentPrivateKey, keyPair.publicKey))
    )

    return await UsersApi.changePassword({
        email: params.email,
        currentPassword,
        newPassword,
        keyPair: { algorithm: "Rsa", publicKey: keyPair.publicKey, encryptedPrivateKey },
        collectionEncryptionKeys: updatedKeys,
    })
})

const recryptSingleKey = async (key: EncryptionKey, currentPrivateKey: string, newPublicKey: string) => {
    const plainText = await CryptoApi.asymmetricDecrypt(key.key, currentPrivateKey)
    const reencrypted = await CryptoApi.asymmetricEncrypt(plainText, newPublicKey)
    return { key: reencrypted, collectionId: key.collectionId, version: key.version }
}

const fetchAndDecryptKeyPairEpic = createEpic<string, KeyPair, Error>(
    fetchAndDecryptKeyPairAction,
    async (masterKey) => {
        const encryptedKeyPair = await UsersApi.fetchKeyPair()
        const decryptedPrivateKey = await CryptoApi.decrypt(encryptedKeyPair.encryptedPrivateKey, masterKey)
        return { publicKey: encryptedKeyPair.publicKey, privateKey: decryptedPrivateKey }
    }
)

const fetchAndDecryptKeyPairOnMasterKeySet: Epic<AnyAction, AnyAction, AppState> = (action$) =>
    action$.pipe(
        filter(computeMasterKeyAction.done.match),
        map((action) => fetchAndDecryptKeyPairAction.started(action.payload.result))
    )

const computeMasterKeyEpic = createEpic<{ password: string; emailHash: string }, string, Error>(
    computeMasterKeyAction,
    (params) => CryptoApi.deriveKey(params.password, params.emailHash.toLowerCase())
)

export const usersEpics = combineEpics<Action, Action, AppState>(
    userLoginEpic,
    currentUserDataEpic,
    setNotLoggedInOnUnauthorizedEpic,
    registerUserEpic,
    activateUserEpic,
    setMasterPassOnLoginEpic,
    requestPasswordResetEpic,
    resetPasswordEpic,
    updateUserDataEpic,
    refreshUserDataEpic,
    fetchApiKeysEpic,
    createApiKeyEpic,
    updateApiKeyEpic,
    deleteApiKeyEpic,
    apiLogoutEpic,
    localLogoutOnApiLogoutEpic,
    changePasswordEpic,
    fetchAndDecryptKeyPairEpic,
    fetchAndDecryptKeyPairOnMasterKeySet,
    computeMasterKeyEpic
)
