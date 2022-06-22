import { PaginationRequest } from "./../../../application/api/Pagination"
import { UserContact } from "./../types/UserContact"
import { UserContactSchema } from "../types/UserContact"
import { UserPublicData, UserPublicDataSchema } from "./../types/UserPublicData"
import { EncryptionKey } from "./../../collection/types/EncryptionKey"
import {
    EncryptedKeyPair,
    EncryptedKeyPairSchema,
    PublicKeyData,
    publicKeyDataSchema,
} from "./../types/EncryptedKeyPair"
import { OptionalValue } from "./../../../application/api/OptionalValue"
import { ApiKeyDescription, ApiKeyDescriptionSchema } from "./../types/ApiKey"
import {
    errorResponseReasonToError,
    errorResponseToData,
    validatedResponse,
    validatePagedResponse,
} from "./../../../application/api/helpers"
import { HTTPError } from "ky"
import { client } from "../../../application/api/BaseClient"
import { UserData, UserDataSchema } from "./../types/UserData"
import { z } from "zod"
import {
    InvalidCredentials,
    InvalidEmail,
    PasswordsAreEqual,
    EmailAlreadyRegistered,
    NicknameAlreadyRegistered,
    TooManyUsersToFetch,
    ContactWithAliasAlreadyExists,
    UserAlreadyInContacts,
    AddSelfToContacts,
    NickNameQueryTooShort,
} from "./errors"
import { Pagination } from "../../../application/api/Pagination"

export type UserDataWithToken = {
    userData: UserData
    csrfToken: string
}

export type ApiKeyUpdateData = {
    description?: string | null
    enabled?: boolean | null
    validTo?: OptionalValue<string> | null
}

export type ChangePasswordData = {
    email: string
    currentPassword: string
    newPassword: string
    keyPair: EncryptedKeyPair
    collectionEncryptionKeys: EncryptionKey[]
}

export type ContactUpdateData = { alias?: OptionalValue<string> | null }

export type FindUserByNickNameData = {
    nickNameStart: string
    includeSelf?: boolean
    excludeContacts?: boolean
} & PaginationRequest

const csrfTokenSchema = z.string().min(10)

const extractUserData: (r: Response) => Promise<UserDataWithToken> = async (response: Response) => {
    const json = await response.json()
    const userData = UserDataSchema.parse(json)
    const tokenHeaderValue = response.headers.get("X-Csrf-Token") ?? ""
    const csrfToken = csrfTokenSchema.parse(tokenHeaderValue)
    return { userData, csrfToken }
}

const multipleUsersResultSchema = z.record(z.string(), UserPublicDataSchema.nullish())

const api = {
    login(email: string, password: string): Promise<UserDataWithToken | null> {
        return client
            .post("users/auth/login", { json: { email, password } })
            .then(extractUserData)
            .catch((err) => {
                if (err instanceof HTTPError && [401, 400].includes(err.response.status)) {
                    return null
                } else {
                    return Promise.reject(err)
                }
            })
    },
    fetchCurrentUserData(): Promise<UserDataWithToken> {
        return client.get("users/me/data").then(extractUserData)
    },
    registerUser(
        nickName: string,
        email: string,
        password: string,
        keyPair: EncryptedKeyPair,
        language?: string
    ): Promise<void> {
        return client
            .post("users", { json: { nickName, email, password, language, keyPair } })
            .then(() => void 0)
            .catch((err) =>
                errorResponseReasonToError(err, new EmailAlreadyRegistered(email), 409, "EmailAlreadyRegistered")
            )
            .catch((err) =>
                errorResponseReasonToError(
                    err,
                    new NicknameAlreadyRegistered(nickName),
                    409,
                    "NickNameAlreadyRegistered"
                )
            )
    },
    activateAccount(token: string): Promise<boolean> {
        return client
            .get(`users/registrations/${token}`)
            .then(() => true)
            .catch((err) => errorResponseToData(err, false, 404))
    },
    requestPasswordReset(email: string): Promise<void> {
        return client.post("users/password", { json: { email } }).then(() => void 0)
    },
    resetPassword(resetToken: string, newPassword: string, keyPair: EncryptedKeyPair, email: string): Promise<boolean> {
        return client
            .put(`users/password/${resetToken}`, { json: { password: newPassword, email, keyPair } })
            .then(() => true)
            .catch((err) => errorResponseToData(err, false, 404))
    },
    updateProfile(data: { nickName?: string | null; language?: string | null }): Promise<UserData> {
        return client
            .patch(`users/me/data`, { json: data })
            .then((resp) => validatedResponse(resp, UserDataSchema))
            .catch((err) =>
                errorResponseReasonToError(
                    err,
                    new NicknameAlreadyRegistered(data.nickName ?? "unknown"),
                    409,
                    "NickNameAlreadyRegistered"
                )
            )
    },
    getApiKeys(): Promise<ApiKeyDescription[]> {
        return client.get("users/me/api-keys").then((resp) => validatedResponse(resp, z.array(ApiKeyDescriptionSchema)))
    },
    createApiKey(description: string, validTo?: string): Promise<ApiKeyDescription> {
        return client
            .post("users/me/api-keys", { json: { description, validTo } })
            .then((resp) => validatedResponse(resp, ApiKeyDescriptionSchema))
    },
    updateApiKey(keyId: string, data: ApiKeyUpdateData): Promise<ApiKeyDescription> {
        return client
            .patch(`users/me/api-keys/${keyId}`, { json: data })
            .then((resp) => validatedResponse(resp, ApiKeyDescriptionSchema))
    },
    deleteApiKey(keyId: string): Promise<void> {
        return client.delete(`users/me/api-keys/${keyId}`).then(() => void 0)
    },
    logout(): Promise<void> {
        return client.delete("users/me/session").then(() => void 0)
    },
    changePassword(data: ChangePasswordData): Promise<void> {
        return client
            .post("users/me/password", { json: data })
            .then(() => void 0)
            .catch((e) => errorResponseReasonToError(e, new PasswordsAreEqual(), 412, "PasswordsEqual"))
            .catch((e) => errorResponseReasonToError(e, new InvalidCredentials(), 412, "InvalidCurrentPassword"))
            .catch((e) => errorResponseReasonToError(e, new InvalidEmail(), 412, "InvalidEmail"))
    },
    fetchKeyPair(): Promise<EncryptedKeyPair> {
        return client.get("users/me/keypair").then((resp) => validatedResponse(resp, EncryptedKeyPairSchema))
    },
    fetchUserPublicData(userId: string): Promise<UserPublicData | null> {
        return client
            .get(`users/${userId}/data`)
            .then((resp) => validatedResponse(resp, UserPublicDataSchema))
            .catch((e) => errorResponseToData(e, null, 400))
            .catch((e) => errorResponseToData(e, null, 404))
    },
    fetchMultipleUsersPublicData(userIds: string[]): Promise<Record<string, UserPublicData | null | undefined>> {
        if (userIds.length > 20) {
            return Promise.reject(new TooManyUsersToFetch())
        }
        const params = new URLSearchParams()
        userIds.forEach((userId) => params.append("userId", userId))
        return client
            .get("users", { searchParams: params })
            .then((resp) => validatedResponse(resp, multipleUsersResultSchema))
    },
    createContact(userId: string, alias?: string): Promise<UserContact> {
        return client
            .post("users/me/contacts", { json: { contactUserId: userId, alias } })
            .then((resp) => {
                return validatedResponse(resp, UserContactSchema)
            })
            .catch((e) =>
                errorResponseReasonToError(
                    e,
                    new ContactWithAliasAlreadyExists(alias ?? "unknown"),
                    409,
                    "ContactAliasAlreadyExists"
                )
            )
            .catch((e) => errorResponseReasonToError(e, new UserAlreadyInContacts(userId), 409, "ContactAlreadyExists"))
            .catch((e) => errorResponseReasonToError(e, new AddSelfToContacts(), 412, "AddSelfToContacts"))
    },
    deleteContact(userId: string): Promise<void> {
        return client.delete(`users/me/contacts/${userId}`).then(() => undefined)
    },
    listContacts(params: { page?: number; limit?: number; nameFilter?: string }): Promise<Pagination<UserContact[]>> {
        const searchParams = Object.fromEntries(Object.entries(params).filter(([key, value]) => value !== undefined))
        const searchParamsOrEmpty = Object.keys(searchParams).length > 0 ? searchParams : undefined
        return client
            .get("users/me/contacts", { searchParams: searchParamsOrEmpty })
            .then((resp) => validatePagedResponse(resp, UserContactSchema.array()))
    },
    editContact(contactId: string, data: ContactUpdateData): Promise<UserContact> {
        return client
            .patch(`users/me/contacts/${contactId}`, { json: data })
            .then((resp) => validatedResponse(resp, UserContactSchema))
            .catch((e) =>
                errorResponseReasonToError(
                    e,
                    new ContactWithAliasAlreadyExists(data.alias?.value ?? "unknown"),
                    409,
                    "ContactAliasAlreadyExists"
                )
            )
    },
    findUserByNickName(data: FindUserByNickNameData): Promise<Pagination<UserPublicData[]>> {
        const { nickNameStart, ...optionalQueryParams } = data
        if (nickNameStart.trim().length < 5) {
            return Promise.reject(new NickNameQueryTooShort(nickNameStart))
        }
        const searchParams = Object.fromEntries(
            Object.entries(optionalQueryParams).filter(([key, value]) => value !== undefined)
        )
        const maybeSearchParams = Object.keys(searchParams).length > 0 ? searchParams : undefined
        return client
            .get(`users/nicknames/${nickNameStart}`, { searchParams: maybeSearchParams })
            .then((resp) => validatePagedResponse(resp, UserPublicDataSchema.array()))
    },
    getPublicKey(userId: string): Promise<PublicKeyData | null> {
        return client
            .get(`users/${userId}/public-key`)
            .then((resp) => validatedResponse(resp, publicKeyDataSchema))
            .catch((error) => errorResponseToData(error, null, 404))
    },
}

export default api
