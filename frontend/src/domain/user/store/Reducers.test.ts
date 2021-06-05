import { ApiKeyDescription } from "./../types/ApiKey"
import { NotLoggedIn } from "../../../application/api/ApiError"
import {
    exampleApiKey,
    exampleUserEmail,
    exampleUserId,
    exampleUserNickname,
    exampleUserPassword,
} from "./../../../testutils/constants/user"
import {
    changePasswordAction,
    computeMasterKeyAction,
    createApiKeyAction,
    deleteApiKeyAction,
    fetchCurrentUserAction,
    localLogoutAction,
    loginAction,
    refreshUserDataAction,
    registerNewUserAction,
    resetLoginResultAction,
    resetPasswordAction,
    resetRefreshUserDataStatusAction,
    updateApiKeyAction,
    updateUserDataAction,
} from "./Actions"
import { usersReducer } from "./Reducers"
const defaultState = {} as ReturnType<typeof usersReducer>

describe("User reducers", () => {
    describe("Login reducer", () => {
        it("should set status to pending", () => {
            const action = loginAction.started({ email: exampleUserEmail, password: exampleUserPassword })
            const result = usersReducer(defaultState, action)
            expect(result.loginStatus).toStrictEqual({ status: "PENDING", params: void 0 })
        })

        it("should set status to finished with true if login succeeded", () => {
            const action = loginAction.done({
                params: { email: exampleUserEmail, password: exampleUserPassword },
                result: {
                    csrfToken: "A-B-C",
                    userData: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
                },
            })
            const result = usersReducer(defaultState, action)
            expect(result.loginStatus).toStrictEqual({ status: "FINISHED", params: void 0, data: true })
        })

        it("should set status to finished with false if login failed", () => {
            const action = loginAction.done({
                params: { email: exampleUserEmail, password: exampleUserPassword },
                result: null,
            })
            const result = usersReducer(defaultState, action)
            expect(result.loginStatus).toStrictEqual({ status: "FINISHED", params: void 0, data: false })
        })

        it("should set status to failed on error", () => {
            const action = loginAction.failed({
                params: { email: exampleUserEmail, password: exampleUserPassword },
                error: new Error("Unknown error"),
            })
            const result = usersReducer(defaultState, action)
            expect(result.loginStatus).toStrictEqual({
                status: "FAILED",
                params: void 0,
                error: new Error("Unknown error"),
            })
        })

        it("should set status to not started on reset action", () => {
            const action = resetLoginResultAction()
            const result = usersReducer(defaultState, action)
            expect(result.loginStatus).toStrictEqual({ status: "NOT_STARTED" })
        })
    })

    describe("User data reducer", () => {
        it("should set status to pending", () => {
            const action = fetchCurrentUserAction.started()
            const result = usersReducer(defaultState, action)
            expect(result.userData).toStrictEqual({ status: "PENDING", params: void 0 })
        })

        it("should set status to finished", () => {
            const action = fetchCurrentUserAction.done({
                params: void 0,
                result: {
                    csrfToken: "A-B-C",
                    userData: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
                },
            })
            const result = usersReducer(defaultState, action)
            expect(result.userData).toStrictEqual({
                status: "FINISHED",
                params: void 0,
                data: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
            })
        })

        it("should set status to failed", () => {
            const action = fetchCurrentUserAction.failed({
                params: void 0,
                error: new Error("Unknown error"),
            })
            const result = usersReducer(defaultState, action)
            expect(result.userData).toStrictEqual({
                status: "FAILED",
                params: void 0,
                error: new Error("Unknown error"),
            })
        })

        it("should set status to finished on login success", () => {
            const action = loginAction.done({
                params: { email: exampleUserEmail, password: exampleUserPassword },
                result: {
                    csrfToken: "A-B-C",
                    userData: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
                },
            })
            const result = usersReducer(defaultState, action)
            expect(result.userData).toStrictEqual({
                status: "FINISHED",
                params: void 0,
                data: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
            })
        })

        it("should set status to finished on update user data success", () => {
            const action = updateUserDataAction.done({
                params: { nickName: exampleUserNickname },
                result: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
            })
            const result = usersReducer(defaultState, action)
            expect(result.userData).toStrictEqual({
                status: "FINISHED",
                params: void 0,
                data: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
            })
        })

        it("should set status to failed with not logged in on local logout", () => {
            const action = localLogoutAction()
            const result = usersReducer(defaultState, action)
            expect(result.userData).toStrictEqual({
                status: "FAILED",
                params: void 0,
                error: new NotLoggedIn(),
            })
        })

        it("should set status to finished on refresh data success", () => {
            const action = refreshUserDataAction.done({
                params: void 0,
                result: {
                    csrfToken: "A-B-C",
                    userData: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
                },
            })
            const result = usersReducer(defaultState, action)
            expect(result.userData).toStrictEqual({
                status: "FINISHED",
                params: void 0,
                data: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
            })
        })
    })

    describe("CSRF token reducer", () => {
        it("should set value on successful login", () => {
            const action = loginAction.done({
                params: { email: exampleUserEmail, password: exampleUserPassword },
                result: {
                    csrfToken: "A-B-C",
                    userData: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
                },
            })
            const state = { ...defaultState, csrfToken: "X-Y-Z" }
            const result = usersReducer(state, action)
            expect(result.csrfToken).toEqual("A-B-C")
        })

        it("should set keep previous value on failed login", () => {
            const action = loginAction.done({
                params: { email: exampleUserEmail, password: exampleUserPassword },
                result: null,
            })
            const state = { ...defaultState, csrfToken: "X-Y-Z" }
            const result = usersReducer(state, action)
            expect(result.csrfToken).toEqual("X-Y-Z")
        })

        it("should set value on user data fetch", () => {
            const action = fetchCurrentUserAction.done({
                params: void 0,
                result: {
                    csrfToken: "A-B-C",
                    userData: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
                },
            })
            const state = { ...defaultState, csrfToken: "X-Y-Z" }
            const result = usersReducer(state, action)
            expect(result.csrfToken).toEqual("A-B-C")
        })

        it("should set value on user data refresh", () => {
            const action = refreshUserDataAction.done({
                params: void 0,
                result: {
                    csrfToken: "A-B-C",
                    userData: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
                },
            })
            const state = { ...defaultState, csrfToken: "X-Y-Z" }
            const result = usersReducer(state, action)
            expect(result.csrfToken).toEqual("A-B-C")
        })

        it("should set value to null on local logout", () => {
            const action = localLogoutAction()
            const state = { ...defaultState, csrfToken: "X-Y-Z" }
            const result = usersReducer(state, action)
            expect(result.csrfToken).toBe(null)
        })
    })

    describe("Register user reducer", () => {
        it("should drop password param", () => {
            const action = registerNewUserAction.done({
                params: {
                    nickname: exampleUserNickname,
                    email: exampleUserEmail,
                    language: "Pl",
                    password: exampleUserPassword,
                },
                result: void 0,
            })
            const result = usersReducer(defaultState, action)
            expect(result.userRegistration).toStrictEqual({
                status: "FINISHED",
                params: { nickname: exampleUserNickname, email: exampleUserEmail, language: "Pl", password: "" },
                data: void 0,
            })
        })
    })

    describe("Master key reducer", () => {
        it("should drop password param on compute", () => {
            const action = computeMasterKeyAction.done({ params: exampleUserPassword, result: "ABCD" })
            const result = usersReducer(defaultState, action)
            expect(result.masterKey).toStrictEqual({ status: "FINISHED", params: "", data: "ABCD" })
        })

        it("should set status to not started on local logout", () => {
            const action = localLogoutAction()
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                masterKey: { status: "FINISHED", params: "", data: "ABCD" },
            }
            const result = usersReducer(state, action)
            expect(result.masterKey).toStrictEqual({ status: "NOT_STARTED" })
        })

        it("should set status to not started on password change", () => {
            const action = changePasswordAction.done({
                params: { email: exampleUserEmail, currentPassword: exampleUserPassword, newPassword: "new-password" },
                result: void 0,
            })
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                masterKey: { status: "FINISHED", params: "", data: "ABCD" },
            }
            const result = usersReducer(state, action)
            expect(result.masterKey).toStrictEqual({ status: "NOT_STARTED" })
        })
    })

    describe("Reset password reducer", () => {
        it("should drop password and token params", () => {
            const action = resetPasswordAction.done({
                params: { resetToken: "A-B-C", newPassword: exampleUserPassword, email: exampleUserEmail },
                result: true,
            })
            const result = usersReducer(defaultState, action)
            expect(result.resetPassword).toStrictEqual({
                status: "FINISHED",
                params: { resetToken: "", newPassword: "", email: exampleUserEmail },
                data: true,
            })
        })
    })

    describe("Refresh user data reducer", () => {
        it("should set status to pending", () => {
            const action = refreshUserDataAction.started()
            const result = usersReducer(defaultState, action)
            expect(result.refreshUserDataStatus).toStrictEqual({ status: "PENDING", params: void 0 })
        })

        it("should set status to finished", () => {
            const action = refreshUserDataAction.done({
                params: void 0,
                result: {
                    csrfToken: "A-B-C",
                    userData: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
                },
            })
            const result = usersReducer(defaultState, action)
            expect(result.refreshUserDataStatus).toStrictEqual({ status: "FINISHED", params: void 0, data: void 0 })
        })

        it("should set status to failed", () => {
            const action = refreshUserDataAction.failed({ params: void 0, error: new Error("Unknown error") })
            const result = usersReducer(defaultState, action)
            expect(result.refreshUserDataStatus).toStrictEqual({
                status: "FAILED",
                params: void 0,
                error: new Error("Unknown error"),
            })
        })

        it("should set status to not started on reset action", () => {
            const action = resetRefreshUserDataStatusAction()
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                refreshUserDataStatus: { status: "PENDING", params: void 0 },
            }
            const result = usersReducer(state, action)
            expect(result.refreshUserDataStatus).toStrictEqual({ status: "NOT_STARTED" })
        })
    })

    describe("Fetch API key reducer", () => {
        it("sould modify API key on update action success", () => {
            const apiKey1 = exampleApiKey
            const apiKey2: ApiKeyDescription = {
                ...exampleApiKey,
                id: "7a8b5c39-e982-4934-9f15-563b913e2048",
                description: "key2",
            }
            const apiKey3: ApiKeyDescription = {
                ...exampleApiKey,
                id: "7893193e-b83e-497b-b493-1e53602a6d09",
                description: "key3",
            }
            const action = updateApiKeyAction.done({
                params: { keyId: apiKey2.id, data: { description: "new-description", enabled: false } },
                result: { ...apiKey2, description: "new-description", enabled: false },
            })
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                apiKeys: { status: "FINISHED", params: void 0, data: [apiKey1, apiKey2, apiKey3] },
            }
            const result = usersReducer(state, action)
            expect(result.apiKeys).toStrictEqual({
                status: "FINISHED",
                params: void 0,
                data: [apiKey1, { ...apiKey2, description: "new-description", enabled: false }, apiKey3],
            })
        })

        it("should delete API key on delete action success", () => {
            const apiKey1 = exampleApiKey
            const apiKey2: ApiKeyDescription = {
                ...exampleApiKey,
                id: "7a8b5c39-e982-4934-9f15-563b913e2048",
                description: "key2",
            }
            const apiKey3: ApiKeyDescription = {
                ...exampleApiKey,
                id: "7893193e-b83e-497b-b493-1e53602a6d09",
                description: "key3",
            }
            const action = deleteApiKeyAction.done({ params: apiKey2.id, result: void 0 })
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                apiKeys: { status: "FINISHED", params: void 0, data: [apiKey1, apiKey2, apiKey3] },
            }
            const result = usersReducer(state, action)
            expect(result.apiKeys).toStrictEqual({
                status: "FINISHED",
                params: void 0,
                data: [apiKey1, apiKey3],
            })
        })

        it("should insert API key on create action success", () => {
            const apiKey1 = exampleApiKey
            const apiKey2: ApiKeyDescription = {
                ...exampleApiKey,
                id: "7a8b5c39-e982-4934-9f15-563b913e2048",
                description: "key2",
            }
            const apiKey3: ApiKeyDescription = {
                ...exampleApiKey,
                id: "7893193e-b83e-497b-b493-1e53602a6d09",
                description: "key3",
            }
            const newApiKey: ApiKeyDescription = {
                ...exampleApiKey,
                id: "9f2dd0e1-850d-4dfa-8939-784c27645ef8",
                description: "new-key",
            }
            const action = createApiKeyAction.done({
                params: { description: newApiKey.description, validTo: newApiKey.validTo ?? undefined },
                result: newApiKey,
            })
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                apiKeys: { status: "FINISHED", params: void 0, data: [apiKey1, apiKey2, apiKey3] },
            }
            const result = usersReducer(state, action)
            expect(result.apiKeys).toEqual({
                status: "FINISHED",
                params: void 0,
                data: [newApiKey, apiKey1, apiKey2, apiKey3],
            })
        })
    })

    describe("Change password reducer", () => {
        it("should drop password from params", () => {
            const action = changePasswordAction.done({
                params: { email: exampleUserEmail, currentPassword: exampleUserPassword, newPassword: "new-password" },
                result: void 0,
            })
            const result = usersReducer(defaultState, action)
            expect(result.changePassword).toStrictEqual({
                status: "FINISHED",
                params: { email: exampleUserEmail, currentPassword: "", newPassword: "" },
                data: void 0,
            })
        })
    })
})
