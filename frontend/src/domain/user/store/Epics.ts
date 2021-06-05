import { NotLoggedIn } from "./../../../application/api/ApiError"
import { ApiKeyDescription } from "./../types/ApiKey"
import { UserData } from "./../types/UserData"
import { EMPTY, from } from "rxjs"
import { mergeMap } from "rxjs/internal/operators/mergeMap"
import { filter, map, catchError } from "rxjs/operators"
import { ApiKeyUpdateData, ChangePasswordData, UserDataWithToken } from "./../api/UsersApi"
import { Action, AnyAction } from "redux"
import { combineEpics, Epic } from "redux-observable"
import { createEpic } from "./../../../application/store/async/AsyncActionEpic"
import {
    activateAccountAction,
    apiLogoutAction,
    changePasswordAction,
    computeMasterKeyAction,
    createApiKeyAction,
    deleteApiKeyAction,
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

const hashEmail = (email: string) => {
    const messageDigest = forge.md.sha256.create()
    messageDigest.update(email.trim().toLocaleLowerCase())
    return messageDigest.digest().toHex()
}

const generateLoginPassword = async (userPassword: string, email: string) => {
    const hashedEmail = hashEmail(email)
    const masterKey = await CryptoApi.deriveKey(userPassword, hashedEmail)
    return await CryptoApi.deriveKey(masterKey, hashedEmail)
}

const userLoginEpic = createEpic<{ email: string; password: string }, UserDataWithToken | null, Error>(
    loginAction,
    (params) =>
        generateLoginPassword(params.password, params.email).then((loginKey) => UsersApi.login(params.email, loginKey))
)

const currentUserDataEpic = createEpic<void, UserDataWithToken, Error>(fetchCurrentUserAction, () =>
    UsersApi.fetchCurrentUserData()
)

const setNotLoggedInOnUnauthorizedEpic: Epic<AnyAction, AnyAction, AppState> = (action$) =>
    action$.pipe(
        filter((a) => a.payload?.error instanceof Unauthorized && a.payload?.error?.needLogout === true),
        map(() => fetchCurrentUserAction.failed({ params: void 0, error: new NotLoggedIn() }))
    )

const registerUserEpic = createEpic<RegistrationParams, void, Error>(registerNewUserAction, (params) =>
    generateLoginPassword(params.password, params.email).then((loginPassword) =>
        UsersApi.registerUser(params.nickname, params.email, loginPassword, params.language)
    )
)

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
                        computeMasterKeyAction.done({ result: result, params: action.payload.params.password })
                    ),
                    catchError((err: Error) => [
                        computeMasterKeyAction.failed({ params: action.payload.params.password, error: err }),
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
    ({ resetToken, newPassword, email }) =>
        generateLoginPassword(newPassword, email).then((loginPassword) =>
            UsersApi.resetPassword(resetToken, loginPassword, email)
        )
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

const changePasswordEpic = createEpic<ChangePasswordData, void, Error>(changePasswordAction, async (params) => {
    const currentPassword = await generateLoginPassword(params.currentPassword, params.email)
    const newPassword = await generateLoginPassword(params.newPassword, params.email)
    return await UsersApi.changePassword({ email: params.email, currentPassword, newPassword })
})

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
    changePasswordEpic
)
