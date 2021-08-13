import { EncryptionKey } from "./../../collection/types/EncryptionKey"
import { EncryptedKeyPair, EncryptedKeyPairSchema } from "./../types/EncryptedKeyPair"
import { OptionalValue } from "./../../../application/api/OptionalValue"
import { ApiKeyDescription, ApiKeyDescriptionSchema } from "./../types/ApiKey"
import { errorResponseReasonToError, errorResponseToData, validatedResponse } from "./../../../application/api/helpers"
import { HTTPError } from "ky"
import { client } from "../../../application/api/BaseClient"
import { UserData, UserDataSchema } from "./../types/UserData"
import { z } from "zod"
import { InvalidCredentials, InvalidEmail, PasswordsAreEqual, UserAlreadyRegistered } from "./errors"

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

const csrfTokenSchema = z.string().min(10)

const extractUserData: (r: Response) => Promise<UserDataWithToken> = async (response: Response) => {
    const json = await response.json()
    const userData = UserDataSchema.parse(json)
    const tokenHeaderValue = response.headers.get("X-Csrf-Token") ?? ""
    const csrfToken = csrfTokenSchema.parse(tokenHeaderValue)
    return { userData, csrfToken }
}

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
            .catch((err) => {
                if (err instanceof HTTPError && err.response.status === 409) {
                    return Promise.reject(new UserAlreadyRegistered(email))
                } else {
                    return Promise.reject(err)
                }
            })
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
        return client.patch(`users/me/data`, { json: data }).then((resp) => validatedResponse(resp, UserDataSchema))
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
}

export default api
