import { FindUserByNickNameData } from "./../api/UsersApi"
import { ContactsFilter } from "./../types/ContactsFilter"
import { Pagination, PaginationRequest } from "./../../../application/api/Pagination"
import { UserContact } from "./../types/UserContact"
import { UserPublicData } from "./../types/UserPublicData"
import { KeyPair } from "./../../../application/cryptography/types/KeyPair"
import { ApiKeyDescription } from "./../types/ApiKey"
import { UserData } from "./../types/UserData"
import actionCreatorFactory from "typescript-fsa"
import { ApiKeyUpdateData, ChangePasswordData, ContactUpdateData, UserDataWithToken } from "../api/UsersApi"

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

export type NewContactParams = {
    userId: string
    alias?: string
}

export type ChangePasswordInputData = Omit<ChangePasswordData, "keyPair" | "collectionEncryptionKeys">

export type EditContactParams = { contactId: string; data: ContactUpdateData }

export const loginAction = actionCreator.async<{ email: string; password: string }, UserDataWithToken | null, Error>(
    "LOGIN"
)
export const resetLoginResultAction = actionCreator("RESET_LOGIN_RESULT")
export const fetchCurrentUserAction = actionCreator.async<void, UserDataWithToken, Error>("FETCH_CURRENT_USER")
export const registerNewUserAction = actionCreator.async<RegistrationParams, void, Error>("REGISTER_NEW_USER")
export const resetRegistrationStatusAction = actionCreator("RESET_REGISTRATION_STATUS")
export const activateAccountAction = actionCreator.async<string, boolean, Error>("ACTIVATE_USER")
export const resetActivationStatusAction = actionCreator("RESET_ACTIVATION_STATUS")
export const computeMasterKeyAction = actionCreator.async<{ password: string; emailHash: string }, string, Error>(
    "COMPUTE_MASTER_KEY"
)
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
export const changePasswordAction = actionCreator.async<ChangePasswordInputData, void, Error>("CHANGE_PASSWORD")
export const resetChangePasswordStatusAction = actionCreator("RESET_CHANGE_PASSWORD_STATUS")
export const createApiKeyAction = actionCreator.async<
    { description: string; validTo?: string },
    ApiKeyDescription,
    Error
>("CREATE_API_KEY")
export const resetCreateApiKeyStatusAction = actionCreator("RESET_CREATE_API_KEY_STATUS")
export const fetchAndDecryptKeyPairAction = actionCreator.async<string, KeyPair, Error>("FETCH_KEYPAIR")
export const resetKeyPairAction = actionCreator("RESET_KEYPAIR")
export const fetchUserPublicDataAction = actionCreator.async<string, UserPublicData | null, Error>(
    "FETCH_USER_PUBLIC_DATA"
)
export const resetFetchUserPublicDataResultAction = actionCreator("RESET_FETCH_USER_PUBLIC_DATA_RESULT")
export const fetchMultipleUsersPublicDataAction = actionCreator.async<
    string[],
    Record<string, UserPublicData | null | undefined>,
    Error
>("FETCH_MULTIPLE_USERS_PUBLIC_DATA")
export const resetKnownUsersAction = actionCreator("RESET_KNOWN_USERS")
export const createContactAction = actionCreator.async<NewContactParams, UserContact, Error>("CREATE_CONTACT")
export const resetCreateContactResultAction = actionCreator("RESET_CREATE_CONTACT_RESULT")
export const deleteContactAction = actionCreator.async<string, void, Error>("DELETE_CONTACT")
export const resetDeleteContactResultAction = actionCreator("RESET_DELETE_CONTACT_RESULT")
export const listContactsAction = actionCreator.async<
    PaginationRequest & { nameFilter?: string },
    Pagination<UserContact[]>,
    Error
>("LIST_CONTACTS")
export const resetListContactsResultAction = actionCreator("RESET_LIST_CONTACTS_RESULT")
export const updateContactsFilterAction = actionCreator<Partial<ContactsFilter>>("UPDATE_CONTACTS_FILTER")
export const requestContactDeleteAction = actionCreator<UserContact | null>("REQUEST_CONTACT_DELETE")
export const refreshContactsListWithParamAction = actionCreator<{
    page?: number | null
    limit?: number | null
    nameFilter?: string | null
}>("REFRESH_CONTACTS_LIST_WITH_PARAM")
export const editContactAction = actionCreator.async<EditContactParams, UserContact, Error>("EDIT_CONTACT")
export const resetEditContactResultAction = actionCreator("RESET_EDIT_CONTACT_RESULT")
export const requestContactEditAction = actionCreator<UserContact | null>("REQUEST_CONTACT_EDIT")
export const findUsersAction = actionCreator.async<FindUserByNickNameData, Pagination<UserPublicData[]>, Error>(
    "FIND_USERS"
)
export const resetFindUsersResultAction = actionCreator("RESET_FIND_USERS_ACTION")
