import { NotLoggedIn } from "./../../../application/api/ApiError"
import { createReducer } from "./../../../application/store/async/AsyncActionReducer"
import {
    activateAccountAction,
    computeMasterKeyAction,
    fetchCurrentUserAction,
    loginAction,
    registerNewUserAction,
    resetActivationStatusAction,
    resetLoginResultAction,
    resetRegistrationStatusAction,
    clearMasterKeyAction,
    requestPasswordResetAction,
    clearPasswordResetRequestStatusAction,
    resetPasswordAction,
    clearResetPasswordStatusAction,
    updateUserDataAction,
    resetUpdateUserDataStatusAction,
    localLogoutAction,
    refreshUserDataAction,
    resetRefreshUserDataStatusAction,
    fetchApiKeysAction,
    resetFetchApiKeysStatusAction,
    updateApiKeyAction,
    resetUpdateApiKeyStatusAction,
    deleteApiKeyAction,
    resetDeleteApiKeyStatusAction,
    apiLogoutAction,
    resetApiLogoutStatusAction,
    changePasswordAction,
    resetChangePasswordStatusAction,
    createApiKeyAction,
    resetCreateApiKeyStatusAction,
    fetchAndDecryptKeyPairAction,
    resetKeyPairAction,
} from "./Actions"
import { AsyncOperationResult } from "./../../../application/store/async/AsyncOperationResult"
import { reducerWithInitialState } from "typescript-fsa-reducers"
import { combineReducers } from "redux"
import { UserData } from "../types/UserData"

const loginReducer = reducerWithInitialState<AsyncOperationResult<void, boolean, Error>>({ status: "NOT_STARTED" })
    .case(loginAction.started, () => ({ status: "PENDING", params: void 0 }))
    .case(loginAction.done, (_, action) => ({ status: "FINISHED", params: void 0, data: action.result !== null }))
    .case(loginAction.failed, (_, action) => ({ status: "FAILED", params: void 0, error: action.error }))
    .case(resetLoginResultAction, () => ({ status: "NOT_STARTED" }))
    .build()

const userDataReducer = reducerWithInitialState<AsyncOperationResult<void, UserData, Error>>({ status: "NOT_STARTED" })
    .case(loginAction.done, (state, action) =>
        action.result !== null ? { status: "FINISHED", params: void 0, data: action.result.userData } : state
    )
    .case(fetchCurrentUserAction.started, (_, action) => ({ status: "PENDING", params: action }))
    .case(fetchCurrentUserAction.done, (_, action) => ({
        status: "FINISHED",
        params: action.params,
        data: action.result.userData,
    }))
    .case(fetchCurrentUserAction.failed, (_, action) => ({
        status: "FAILED",
        params: action.params,
        error: action.error,
    }))
    .case(updateUserDataAction.done, (_, action) => ({ status: "FINISHED", params: void 0, data: action.result }))
    .case(localLogoutAction, () => ({ status: "FAILED", params: void 0, error: new NotLoggedIn() }))
    .case(refreshUserDataAction.done, (_, action) => ({
        status: "FINISHED",
        params: void 0,
        data: action.result.userData,
    }))
    .build()

const csrfTokenReducer = reducerWithInitialState<string | null>(null)
    .case(loginAction.done, (state, action) => (action.result !== null ? action.result.csrfToken : state))
    .case(fetchCurrentUserAction.done, (_, action) => action.result.csrfToken)
    .case(localLogoutAction, () => null)
    .case(refreshUserDataAction.done, (_, action) => action.result.csrfToken)
    .build()

const registerUserReducer = createReducer(registerNewUserAction, resetRegistrationStatusAction, {
    params: (p) => ({ ...p, password: "" }),
}).build()

const activationReducer = createReducer(activateAccountAction, resetActivationStatusAction).build()

const masterKeyReducer = createReducer(computeMasterKeyAction, clearMasterKeyAction, {
    params: ({ emailHash }) => ({ emailHash, password: "" }),
})
    .case(localLogoutAction, () => ({ status: "NOT_STARTED" }))
    .case(changePasswordAction.done, () => ({ status: "NOT_STARTED" }))
    .build()

const requestPasswordResetReducer = createReducer(
    requestPasswordResetAction,
    clearPasswordResetRequestStatusAction
).build()

const resetPasswordReducer = createReducer(resetPasswordAction, clearResetPasswordStatusAction, {
    params: (p) => ({ ...p, newPassword: "", resetToken: "" }),
}).build()

const updateUserDataReducer = createReducer(updateUserDataAction, resetUpdateUserDataStatusAction).build()

const refreshUserDataReducer = reducerWithInitialState<AsyncOperationResult<void, void, Error>>({
    status: "NOT_STARTED",
})
    .case(refreshUserDataAction.started, () => ({ status: "PENDING", params: void 0 }))
    .case(refreshUserDataAction.done, () => ({ status: "FINISHED", data: void 0, params: void 0 }))
    .case(refreshUserDataAction.failed, (_, action) => ({ status: "FAILED", error: action.error, params: void 0 }))
    .case(resetRefreshUserDataStatusAction, () => ({ status: "NOT_STARTED" }))
    .build()

const fetchApiKeysReducer = createReducer(fetchApiKeysAction, resetFetchApiKeysStatusAction)
    .case(updateApiKeyAction.done, (state, action) => {
        if (state.status === "FINISHED") {
            const updated = state.data.map((key) => (key.id === action.params.keyId ? action.result : key))
            return { ...state, data: updated }
        } else {
            return state
        }
    })
    .case(deleteApiKeyAction.done, (state, action) => {
        if (state.status === "FINISHED") {
            const updated = state.data.filter((key) => key.id !== action.params)
            return { ...state, data: updated }
        } else {
            return state
        }
    })
    .case(createApiKeyAction.done, (state, action) => {
        if (state.status === "FINISHED") {
            const updated = [action.result, ...state.data]
            return { ...state, data: updated }
        } else {
            return state
        }
    })
    .build()

const updateApiKeyReducer = createReducer(updateApiKeyAction, resetUpdateApiKeyStatusAction).build()

const deleteApiKeyReducer = createReducer(deleteApiKeyAction, resetDeleteApiKeyStatusAction).build()

const apiLogoutReducer = createReducer(apiLogoutAction, resetApiLogoutStatusAction).build()

const changePasswordReducer = createReducer(changePasswordAction, resetChangePasswordStatusAction, {
    params: (p) => ({ ...p, newPassword: "", currentPassword: "" }),
}).build()

const createApiKeyReducer = createReducer(createApiKeyAction, resetCreateApiKeyStatusAction).build()

const fetchAndDecryptKeyPairReducer = createReducer(fetchAndDecryptKeyPairAction, resetKeyPairAction, {
    params: (masterKey) => "",
})
    .cases(
        [localLogoutAction, computeMasterKeyAction.failed, computeMasterKeyAction.started, clearMasterKeyAction],
        () => ({ status: "NOT_STARTED" })
    )
    .build()

export const usersReducer = combineReducers({
    loginStatus: loginReducer,
    userData: userDataReducer,
    csrfToken: csrfTokenReducer,
    userRegistration: registerUserReducer,
    accountActivation: activationReducer,
    masterKey: masterKeyReducer,
    passwordResetRequest: requestPasswordResetReducer,
    resetPassword: resetPasswordReducer,
    updateUserDataStatus: updateUserDataReducer,
    refreshUserDataStatus: refreshUserDataReducer,
    apiKeys: fetchApiKeysReducer,
    createApiKeyStatus: createApiKeyReducer,
    updateApiKeyStatus: updateApiKeyReducer,
    deleteApiKeyStatus: deleteApiKeyReducer,
    logoutStatus: apiLogoutReducer,
    changePassword: changePasswordReducer,
    keyPair: fetchAndDecryptKeyPairReducer,
})
