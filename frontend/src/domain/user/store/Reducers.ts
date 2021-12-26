import { setActiveCollectionAction } from "./../../collection/store/Actions"
import { UserContact } from "./../types/UserContact"
import { ContactsFilter } from "./../types/ContactsFilter"
import { UserPublicData } from "./../types/UserPublicData"
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
    fetchUserPublicDataAction,
    resetFetchUserPublicDataResultAction,
    fetchMultipleUsersPublicDataAction,
    createContactAction,
    resetCreateContactResultAction,
    deleteContactAction,
    resetDeleteContactResultAction,
    listContactsAction,
    resetListContactsResultAction,
    updateContactsFilterAction,
    requestContactDeleteAction,
    requestContactEditAction,
    editContactAction,
    resetEditContactResultAction,
} from "./Actions"
import { AsyncOperationResult } from "./../../../application/store/async/AsyncOperationResult"
import { reducerWithInitialState } from "typescript-fsa-reducers"
import { combineReducers } from "redux"
import { UserData } from "../types/UserData"
import identity from "lodash/identity"

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

const fetchPublicDataReducer = createReducer(fetchUserPublicDataAction, resetFetchUserPublicDataResultAction)
    .case(createContactAction.done, (state, action) => {
        if (state.status === "FINISHED" && state.params === action.params.userId && state.data) {
            const updated: UserPublicData = {
                ...state.data,
                nickName: action.result.nickName,
                contactData: { alias: action.result.alias },
            }
            return { ...state, data: updated }
        } else {
            return state
        }
    })
    .case(deleteContactAction.done, (state, action) => {
        if (state.status === "FINISHED" && state.params === action.params && state.data) {
            const updated: UserPublicData = { ...state.data, contactData: null }
            return { ...state, data: updated }
        } else {
            return state
        }
    })
    .case(editContactAction.done, (state, action) => {
        if (state.status === "FINISHED" && state.data?.userId === action.result.userId) {
            const updated: UserPublicData = { ...state.data, contactData: { alias: action.result.alias } }
            return { ...state, data: updated }
        } else {
            return state
        }
    })
    .build()

type OptionalUserPublicData = UserPublicData | null | undefined

type PublicUserResult = AsyncOperationResult<string, OptionalUserPublicData, Error>

const updateKnownUsers = <A>(
    state: Record<string, PublicUserResult>,
    action: A,
    toResult: (a: A, userId: string) => PublicUserResult,
    getParams: (a: A) => string[],
    updateFinished: boolean
) => {
    const resultsNotFinishedBefore = updateFinished
        ? getParams(action)
        : getParams(action).filter((userId) => state[userId]?.status !== "FINISHED")
    const newValues: [string, PublicUserResult][] = resultsNotFinishedBefore.map((userId) => [
        userId,
        toResult(action, userId),
    ])
    const obj = Object.fromEntries(newValues)
    return Object.assign({}, state, obj)
}

const knownUsersReducer = reducerWithInitialState<Record<string, PublicUserResult>>({})
    .case(fetchMultipleUsersPublicDataAction.started, (state, action) => {
        return updateKnownUsers(state, action, (_, userId) => ({ status: "PENDING", params: userId }), identity, false)
    })
    .case(fetchMultipleUsersPublicDataAction.failed, (state, action) => {
        return updateKnownUsers(
            state,
            action,
            (a, userId) => ({ status: "FAILED", params: userId, error: a.error }),
            (a) => a.params,
            false
        )
    })
    .case(fetchMultipleUsersPublicDataAction.done, (state, action) => {
        return updateKnownUsers(
            state,
            action,
            (a, userId) => ({ status: "FINISHED", params: userId, data: a.result[userId] }),
            (a) => a.params,
            true
        )
    })
    .case(createContactAction.done, (state, action) => {
        const maybeUser = state[action.params.userId]
        if (!maybeUser || maybeUser.status !== "FINISHED" || !maybeUser.data) {
            return state
        }
        const updatedUser: PublicUserResult = {
            ...maybeUser,
            data: { ...maybeUser.data, nickName: action.result.nickName, contactData: { alias: action.result.alias } },
        }
        return { ...state, [action.params.userId]: updatedUser }
    })
    .case(deleteContactAction.done, (state, action) => {
        const maybeUser = state[action.params]
        if (!maybeUser || maybeUser.status !== "FINISHED" || !maybeUser.data) {
            return state
        }
        const updatedUser: PublicUserResult = { ...maybeUser, data: { ...maybeUser.data, contactData: null } }
        return { ...state, [action.params]: updatedUser }
    })
    .case(setActiveCollectionAction, () => ({}))
    .case(editContactAction.done, (state, action) => {
        const maybeUser = state[action.result.userId]
        if (!maybeUser || maybeUser.status !== "FINISHED" || !maybeUser.data?.contactData) {
            return state
        }
        const updatedContactData: { alias?: string | null } = { alias: action.result.alias }
        const updatedUser: PublicUserResult = {
            ...maybeUser,
            data: { ...maybeUser.data, contactData: updatedContactData },
        }
        return { ...state, [action.result.userId]: updatedUser }
    })
    .build()

const createContactReducer = createReducer(createContactAction, resetCreateContactResultAction).build()

const deleteContactReducer = createReducer(deleteContactAction, resetDeleteContactResultAction).build()

const listContactsReducer = createReducer(listContactsAction, resetListContactsResultAction).build()

const contactsFilterReducer = reducerWithInitialState<ContactsFilter>({ name: "" })
    .case(updateContactsFilterAction, (state, action) => ({ ...state, name: action.name ?? state.name }))
    .build()

const requestContactDeleteReducer = reducerWithInitialState<UserContact | null>(null)
    .case(requestContactDeleteAction, (_, action) => action)
    .build()

const contactEditRequestReducer = reducerWithInitialState<UserContact | null>(null)
    .case(requestContactEditAction, (_, action) => action)
    .build()

const contactEditReducer = createReducer(editContactAction, resetEditContactResultAction).build()

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
    publicData: fetchPublicDataReducer,
    knownUsers: knownUsersReducer,
    createContactResult: createContactReducer,
    deleteContactResult: deleteContactReducer,
    contacts: listContactsReducer,
    contactsFilter: contactsFilterReducer,
    contactRequestedToDelete: requestContactDeleteReducer,
    contactRequestedToEdit: contactEditRequestReducer,
    editContactResult: contactEditReducer,
})
