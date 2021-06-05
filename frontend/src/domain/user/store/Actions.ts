import { ApiKeyDescription } from "./../types/ApiKey"
import { UserData } from "./../types/UserData"
import actionCreatorFactory from "typescript-fsa"
import { ApiKeyUpdateData, ChangePasswordData, UserDataWithToken } from "../api/UsersApi"

const actionCreator = actionCreatorFactory("USER")

export type RegistrationParams = {
    nickname: string
    email: string
    password: string
    language?: string
}

export type ResetPasswordParams = {
    resetToken: string
    newPassword: string
    email: string
}

export const loginAction = actionCreator.async<{ email: string; password: string }, UserDataWithToken | null, Error>(
    "LOGIN"
)
export const resetLoginResultAction = actionCreator("RESET_LOGIN_RESULT")
export const fetchCurrentUserAction = actionCreator.async<void, UserDataWithToken, Error>("FETCH_CURRENT_USER")
export const registerNewUserAction = actionCreator.async<RegistrationParams, void, Error>("REGISTER_NEW_USER")
export const resetRegistrationStatusAction = actionCreator("RESET_REGISTRATION_STATUS")
export const activateAccountAction = actionCreator.async<string, boolean, Error>("ACTIVATE_USER")
export const resetActivationStatusAction = actionCreator("RESET_ACTIVATION_STATUS")
export const computeMasterKeyAction = actionCreator.async<string, string, Error>("COMPUTE_MASTER_KEY")
export const clearMasterKeyAction = actionCreator("CLEAR_MASTER_KEY")
export const requestPasswordResetAction = actionCreator.async<string, void, Error>("REQUEST_PASSWORD_RESET")
export const clearPasswordResetRequestStatusAction = actionCreator("CLEAR_PASSWORD_RESET_REQUEST")
export const resetPasswordAction = actionCreator.async<ResetPasswordParams, boolean, Error>("RESET_PASSWORD")
export const clearResetPasswordStatusAction = actionCreator("CLEAR_RESET_PASSWORD_STATUS")
export const updateUserDataAction = actionCreator.async<
    { nickName?: string | null; language?: string | null },
    UserData,
    Error
>("UPDATE_USER_DATA_ACTION")
export const resetUpdateUserDataStatusAction = actionCreator("RESET_UPDATE_USER_DATA_STATUS")
export const localLogoutAction = actionCreator("LOCAL_LOGOUT")
export const refreshUserDataAction = actionCreator.async<void, UserDataWithToken, Error>("REFRESH_USER_DATA")
export const resetRefreshUserDataStatusAction = actionCreator("RESET_REFRESH_USER_DATA_STATUS")
export const fetchApiKeysAction = actionCreator.async<void, ApiKeyDescription[], Error>("FETCH_API_KEYS")
export const resetFetchApiKeysStatusAction = actionCreator("RESET_FETCH_API_KEYS_STATUS")
export const updateApiKeyAction = actionCreator.async<
    { keyId: string; data: ApiKeyUpdateData },
    ApiKeyDescription,
    Error
>("UPDATE_API_KEY")
export const resetUpdateApiKeyStatusAction = actionCreator("RESET_UPDATE_API_KEY_STATUS")
export const deleteApiKeyAction = actionCreator.async<string, void, Error>("DELETE_API_KEY")
export const resetDeleteApiKeyStatusAction = actionCreator("RESET_DELETE_API_KEY_STATUS")
export const apiLogoutAction = actionCreator.async<void, void, Error>("API_LOGOUT")
export const resetApiLogoutStatusAction = actionCreator("RESET_API_LOGOUT_STATUS")
export const changePasswordAction = actionCreator.async<ChangePasswordData, void, Error>("CHANGE_PASSWORD")
export const resetChangePasswordStatusAction = actionCreator("RESET_CHANGE_PASSWORD_STATUS")
export const createApiKeyAction = actionCreator.async<
    { description: string; validTo?: string },
    ApiKeyDescription,
    Error
>("CREATE_API_KEY")
export const resetCreateApiKeyStatusAction = actionCreator("RESET_CREATE_API_KEY_STATUS")
