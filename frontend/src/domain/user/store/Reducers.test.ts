import { setActiveCollectionAction } from "./../../collection/store/Actions"
import { ApiKeyDescription } from "./../types/ApiKey"
import { NotLoggedIn } from "../../../application/api/ApiError"
import {
    exampleApiKey,
    exampleContactAlias,
    exampleHashedEmail,
    examplePrivateKey,
    examplePublicKey,
    exampleUserEmail,
    exampleUserId,
    exampleUserNickname,
    exampleUserPassword,
} from "./../../../testutils/constants/user"
import {
    changePasswordAction,
    clearMasterKeyAction,
    computeMasterKeyAction,
    createApiKeyAction,
    createContactAction,
    deleteApiKeyAction,
    deleteContactAction,
    editContactAction,
    fetchAndDecryptKeyPairAction,
    fetchCurrentUserAction,
    fetchMultipleUsersPublicDataAction,
    localLogoutAction,
    loginAction,
    refreshUserDataAction,
    registerNewUserAction,
    requestContactDeleteAction,
    requestContactEditAction,
    resetLoginResultAction,
    resetPasswordAction,
    resetRefreshUserDataStatusAction,
    updateApiKeyAction,
    updateContactsFilterAction,
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
                    userData: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    },
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
                    userData: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    },
                },
            })
            const result = usersReducer(defaultState, action)
            expect(result.userData).toStrictEqual({
                status: "FINISHED",
                params: void 0,
                data: {
                    id: exampleUserId,
                    nickName: exampleUserNickname,
                    language: "Pl",
                    emailHash: exampleHashedEmail,
                },
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
                    userData: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    },
                },
            })
            const result = usersReducer(defaultState, action)
            expect(result.userData).toStrictEqual({
                status: "FINISHED",
                params: void 0,
                data: {
                    id: exampleUserId,
                    nickName: exampleUserNickname,
                    language: "Pl",
                    emailHash: exampleHashedEmail,
                },
            })
        })

        it("should set status to finished on update user data success", () => {
            const action = updateUserDataAction.done({
                params: { nickName: exampleUserNickname },
                result: {
                    id: exampleUserId,
                    nickName: exampleUserNickname,
                    language: "Pl",
                    emailHash: exampleHashedEmail,
                },
            })
            const result = usersReducer(defaultState, action)
            expect(result.userData).toStrictEqual({
                status: "FINISHED",
                params: void 0,
                data: {
                    id: exampleUserId,
                    nickName: exampleUserNickname,
                    language: "Pl",
                    emailHash: exampleHashedEmail,
                },
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
                    userData: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    },
                },
            })
            const result = usersReducer(defaultState, action)
            expect(result.userData).toStrictEqual({
                status: "FINISHED",
                params: void 0,
                data: {
                    id: exampleUserId,
                    nickName: exampleUserNickname,
                    language: "Pl",
                    emailHash: exampleHashedEmail,
                },
            })
        })
    })

    describe("CSRF token reducer", () => {
        it("should set value on successful login", () => {
            const action = loginAction.done({
                params: { email: exampleUserEmail, password: exampleUserPassword },
                result: {
                    csrfToken: "A-B-C",
                    userData: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    },
                },
            })
            const state = { ...defaultState, csrfToken: "X-Y-Z" }
            const result = usersReducer(state, action)
            expect(result.csrfToken).toBe("A-B-C")
        })

        it("should set keep previous value on failed login", () => {
            const action = loginAction.done({
                params: { email: exampleUserEmail, password: exampleUserPassword },
                result: null,
            })
            const state = { ...defaultState, csrfToken: "X-Y-Z" }
            const result = usersReducer(state, action)
            expect(result.csrfToken).toBe("X-Y-Z")
        })

        it("should set value on user data fetch", () => {
            const action = fetchCurrentUserAction.done({
                params: void 0,
                result: {
                    csrfToken: "A-B-C",
                    userData: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    },
                },
            })
            const state = { ...defaultState, csrfToken: "X-Y-Z" }
            const result = usersReducer(state, action)
            expect(result.csrfToken).toBe("A-B-C")
        })

        it("should set value on user data refresh", () => {
            const action = refreshUserDataAction.done({
                params: void 0,
                result: {
                    csrfToken: "A-B-C",
                    userData: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    },
                },
            })
            const state = { ...defaultState, csrfToken: "X-Y-Z" }
            const result = usersReducer(state, action)
            expect(result.csrfToken).toBe("A-B-C")
        })

        it("should set value to null on local logout", () => {
            const action = localLogoutAction()
            const state = { ...defaultState, csrfToken: "X-Y-Z" }
            const result = usersReducer(state, action)
            expect(result.csrfToken).toBeNull()
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
            const action = computeMasterKeyAction.done({
                params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
                result: "ABCD",
            })
            const result = usersReducer(defaultState, action)
            expect(result.masterKey).toStrictEqual({
                status: "FINISHED",
                params: { password: "", emailHash: exampleHashedEmail },
                data: "ABCD",
            })
        })

        it("should set status to not started on local logout", () => {
            const action = localLogoutAction()
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                masterKey: {
                    status: "FINISHED",
                    params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
                    data: "ABCD",
                },
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
                masterKey: {
                    status: "FINISHED",
                    params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
                    data: "ABCD",
                },
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
                    userData: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    },
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

    describe("Fetch and decrypt key pair reducer", () => {
        it("should drop password param on finish", () => {
            const action = fetchAndDecryptKeyPairAction.done({
                params: "secret-password",
                result: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
            })
            const result = usersReducer(defaultState, action)
            expect(result.keyPair).toStrictEqual({
                status: "FINISHED",
                params: "",
                data: { privateKey: examplePrivateKey, publicKey: examplePublicKey },
            })
        })

        it("should reset key pair on local logout", () => {
            const action = localLogoutAction()
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                keyPair: {
                    status: "FINISHED",
                    params: "",
                    data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
                },
            }
            const result = usersReducer(state, action)
            expect(result.keyPair).toStrictEqual({ status: "NOT_STARTED" })
        })

        it("should reset key pair on master key compute start", () => {
            const action = computeMasterKeyAction.started({
                password: exampleUserPassword,
                emailHash: exampleHashedEmail,
            })
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                keyPair: {
                    status: "FINISHED",
                    params: "",
                    data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
                },
            }
            const result = usersReducer(state, action)
            expect(result.keyPair).toStrictEqual({ status: "NOT_STARTED" })
        })

        it("should reset key pair on master key compute failure", () => {
            const action = computeMasterKeyAction.failed({
                params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
                error: new Error("Some error"),
            })
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                keyPair: {
                    status: "FINISHED",
                    params: "",
                    data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
                },
            }
            const result = usersReducer(state, action)
            expect(result.keyPair).toStrictEqual({ status: "NOT_STARTED" })
        })

        it("should reset key pair on master key compute reset", () => {
            const action = clearMasterKeyAction()
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                keyPair: {
                    status: "FINISHED",
                    params: "",
                    data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
                },
            }
            const result = usersReducer(state, action)
            expect(result.keyPair).toStrictEqual({ status: "NOT_STARTED" })
        })
    })

    describe("known users reducer", () => {
        const emptyState: ReturnType<typeof usersReducer> = { ...defaultState, knownUsers: {} }
        const pendingState: ReturnType<typeof usersReducer> = {
            ...defaultState,
            knownUsers: {
                foo: { status: "PENDING", params: "foo" },
                bar: { status: "PENDING", params: "bar" },
            },
        }
        const nullState: ReturnType<typeof usersReducer> = {
            ...defaultState,
            knownUsers: {
                foo: { status: "FINISHED", params: "foo", data: null },
                bar: { status: "FINISHED", params: "foo", data: undefined },
            },
        }
        const errorState: ReturnType<typeof usersReducer> = {
            ...defaultState,
            knownUsers: {
                foo: { status: "FAILED", params: "foo", error: new Error("Some error") },
                bar: { status: "FAILED", params: "bar", error: new Error("Other error") },
            },
        }
        const finishedState: ReturnType<typeof usersReducer> = {
            ...defaultState,
            knownUsers: {
                foo: {
                    status: "FINISHED",
                    params: "foo",
                    data: { userId: "foo", nickName: "n1", contactData: { alias: "a1" } },
                },
                bar: {
                    status: "FINISHED",
                    params: "bar",
                    data: { userId: "bar", nickName: "n2", contactData: { alias: "a2" } },
                },
            },
        }

        describe("on fetch users pending state", () => {
            const action = fetchMultipleUsersPublicDataAction.started(["foo", "bar"])

            it("should add pending result to empty state", () => {
                const result = usersReducer(emptyState, action)
                expect(result.knownUsers).toStrictEqual({
                    foo: { status: "PENDING", params: "foo" },
                    bar: { status: "PENDING", params: "bar" },
                })
            })

            it("should update error result with pending", () => {
                const result = usersReducer(errorState, action)
                expect(result.knownUsers).toStrictEqual({
                    foo: { status: "PENDING", params: "foo" },
                    bar: { status: "PENDING", params: "bar" },
                })
            })

            it("should not update finished status with pending", () => {
                const result = usersReducer(finishedState, action)
                expect(result.knownUsers).toStrictEqual(finishedState.knownUsers)
            })

            it("should not update null status with pending", () => {
                const result = usersReducer(nullState, action)
                expect(result.knownUsers).toStrictEqual(nullState.knownUsers)
            })
        })

        describe("on fetch users failed state", () => {
            const action = fetchMultipleUsersPublicDataAction.failed({
                params: ["foo", "bar"],
                error: new Error("New error"),
            })

            it("should add error result to empty state", () => {
                const result = usersReducer(emptyState, action)
                expect(result.knownUsers).toStrictEqual({
                    foo: { status: "FAILED", params: "foo", error: new Error("New error") },
                    bar: { status: "FAILED", params: "bar", error: new Error("New error") },
                })
            })

            it("should update pending result with new error", () => {
                const result = usersReducer(pendingState, action)
                expect(result.knownUsers).toStrictEqual({
                    foo: { status: "FAILED", params: "foo", error: new Error("New error") },
                    bar: { status: "FAILED", params: "bar", error: new Error("New error") },
                })
            })

            it("should update error result with new error", () => {
                const result = usersReducer(errorState, action)
                expect(result.knownUsers).toStrictEqual({
                    foo: { status: "FAILED", params: "foo", error: new Error("New error") },
                    bar: { status: "FAILED", params: "bar", error: new Error("New error") },
                })
            })

            it("should not update finished status with error", () => {
                const result = usersReducer(finishedState, action)
                expect(result.knownUsers).toStrictEqual(finishedState.knownUsers)
            })

            it("should not update null status with error", () => {
                const result = usersReducer(nullState, action)
                expect(result.knownUsers).toStrictEqual(nullState.knownUsers)
            })
        })

        describe("on fetch users finished state", () => {
            const newResult = {
                foo: { userId: "foo", nickName: "xxx", contactData: { alias: "qwerty" } },
                bar: { userId: "bar", nickName: "yyy", contactData: { alias: "asdfg" } },
            }

            const action = fetchMultipleUsersPublicDataAction.done({
                params: ["foo", "bar"],
                result: newResult,
            })

            it("should add finished result to empty state", () => {
                const result = usersReducer(emptyState, action)
                expect(result.knownUsers).toStrictEqual({
                    foo: { status: "FINISHED", params: "foo", data: newResult.foo },
                    bar: { status: "FINISHED", params: "bar", data: newResult.bar },
                })
            })

            it("should update pending result with finished", () => {
                const result = usersReducer(pendingState, action)
                expect(result.knownUsers).toStrictEqual({
                    foo: { status: "FINISHED", params: "foo", data: newResult.foo },
                    bar: { status: "FINISHED", params: "bar", data: newResult.bar },
                })
            })

            it("should update error result with finished", () => {
                const result = usersReducer(errorState, action)
                expect(result.knownUsers).toStrictEqual({
                    foo: { status: "FINISHED", params: "foo", data: newResult.foo },
                    bar: { status: "FINISHED", params: "bar", data: newResult.bar },
                })
            })

            it("should update finished status with new result", () => {
                const result = usersReducer(finishedState, action)
                expect(result.knownUsers).toStrictEqual({
                    foo: { status: "FINISHED", params: "foo", data: newResult.foo },
                    bar: { status: "FINISHED", params: "bar", data: newResult.bar },
                })
            })

            it("should update null status with finished", () => {
                const result = usersReducer(nullState, action)
                expect(result.knownUsers).toStrictEqual({
                    foo: { status: "FINISHED", params: "foo", data: newResult.foo },
                    bar: { status: "FINISHED", params: "bar", data: newResult.bar },
                })
            })
        })

        describe("on create contact finished", () => {
            it("should do nothing if user not fetched before", () => {
                const action = createContactAction.done({
                    params: { userId: "baz", alias: "qaz" },
                    result: { userId: "baz", nickName: "qux", alias: "qaz" },
                })
                const result = usersReducer(finishedState, action)
                expect(result.knownUsers).toStrictEqual(finishedState.knownUsers)
            })

            it("should do nothing if fetched user has empty value", () => {
                const action = createContactAction.done({
                    params: { userId: "foo", alias: "qaz" },
                    result: { userId: "foo", nickName: "qux", alias: "qaz" },
                })
                const result = usersReducer(nullState, action)
                expect(result.knownUsers).toStrictEqual(nullState.knownUsers)
            })

            it("should do nothing if current status is pending", () => {
                const action = createContactAction.done({
                    params: { userId: "foo", alias: "qaz" },
                    result: { userId: "foo", nickName: "qux", alias: "qaz" },
                })
                const result = usersReducer(pendingState, action)
                expect(result.knownUsers).toStrictEqual(pendingState.knownUsers)
            })

            it("should update nickname and alias if already fetched", () => {
                const action = createContactAction.done({
                    params: { userId: "foo", alias: "qaz" },
                    result: { userId: "foo", nickName: "qux", alias: "qaz" },
                })
                const result = usersReducer(finishedState, action)
                expect(result.knownUsers).toStrictEqual({
                    ...finishedState.knownUsers,
                    foo: {
                        status: "FINISHED",
                        params: "foo",
                        data: { userId: "foo", nickName: "qux", contactData: { alias: "qaz" } },
                    },
                })
            })
        })

        describe("on delete contact finished", () => {
            it("should do nothing if user not fetched before", () => {
                const action = deleteContactAction.done({ params: "baz" })
                const result = usersReducer(finishedState, action)
                expect(result.knownUsers).toStrictEqual(finishedState.knownUsers)
            })

            it("should do nothing if fetched user has empty value", () => {
                const action = deleteContactAction.done({ params: "foo" })
                const result = usersReducer(nullState, action)
                expect(result.knownUsers).toStrictEqual(nullState.knownUsers)
            })

            it("should do nothing if current status is pending", () => {
                const action = deleteContactAction.done({ params: "foo" })
                const result = usersReducer(pendingState, action)
                expect(result.knownUsers).toStrictEqual(pendingState.knownUsers)
            })

            it("should set contact data to null if already fetched", () => {
                const action = deleteContactAction.done({ params: "foo" })
                const result = usersReducer(finishedState, action)
                expect(result.knownUsers).toStrictEqual({
                    ...finishedState.knownUsers,
                    foo: {
                        status: "FINISHED",
                        params: "foo",
                        data: { userId: "foo", nickName: "n1", contactData: null },
                    },
                })
            })
        })

        describe("on collection change", () => {
            it("should reset data", () => {
                const action = setActiveCollectionAction("new-collection")
                const result = usersReducer(finishedState, action)
                expect(result.knownUsers).toStrictEqual({})
            })
        })

        describe("on contact edit finished", () => {
            it("should update contact data", () => {
                const params = { contactId: "foo", data: { alias: { value: "new-alias" } } }
                const action = editContactAction.done({
                    params,
                    result: { userId: "foo", nickName: "n1", alias: "new-alias" },
                })
                const result = usersReducer(finishedState, action)
                expect(result.knownUsers).toStrictEqual({
                    ...finishedState.knownUsers,
                    foo: {
                        status: "FINISHED",
                        params: "foo",
                        data: { userId: "foo", nickName: "n1", contactData: { alias: "new-alias" } },
                    },
                })
            })
            it("should should do nothing if user not fetched before", () => {
                const params = { contactId: "baz", data: { alias: { value: "new-alias" } } }
                const action = editContactAction.done({
                    params,
                    result: { userId: "baz", nickName: "n1", alias: "new-alias" },
                })
                const result = usersReducer(finishedState, action)
                expect(result.knownUsers).toStrictEqual(finishedState.knownUsers)
            })
            it("should do nothing if fetching not finished yet", () => {
                const params = { contactId: "foo", data: { alias: { value: "new-alias" } } }
                const action = editContactAction.done({
                    params,
                    result: { userId: "foo", nickName: "n1", alias: "new-alias" },
                })
                const result = usersReducer(pendingState, action)
                expect(result.knownUsers).toStrictEqual(pendingState.knownUsers)
            })
            it("should do nothing if user data is not set", () => {
                const params = { contactId: "foo", data: { alias: { value: "new-alias" } } }
                const action = editContactAction.done({
                    params,
                    result: { userId: "foo", nickName: "n1", alias: "new-alias" },
                })
                const result = usersReducer(emptyState, action)
                expect(result.knownUsers).toStrictEqual(emptyState.knownUsers)
            })
            it("should do nothing if user has no contact data assigned", () => {
                const params = { contactId: "foo", data: { alias: { value: "new-alias" } } }
                const action = editContactAction.done({
                    params,
                    result: { userId: "foo", nickName: "n1", alias: "new-alias" },
                })
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    knownUsers: {
                        foo: {
                            status: "FINISHED",
                            params: "foo",
                            data: null,
                        },
                    },
                }
                const result = usersReducer(state, action)
                expect(result.knownUsers).toStrictEqual(state.knownUsers)
            })
        })
    })

    describe("fetch user reducer", () => {
        describe("on contact create finished", () => {
            const action = createContactAction.done({
                params: { userId: exampleUserId, alias: "newAlias" },
                result: { userId: exampleUserId, nickName: exampleUserNickname, alias: "newAlias" },
            })

            it("should update nickname and contact data", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "FINISHED",
                        params: exampleUserId,
                        data: { userId: exampleUserId, nickName: "oldNickName", contactData: null },
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual({
                    status: "FINISHED",
                    params: exampleUserId,
                    data: { userId: exampleUserId, nickName: exampleUserNickname, contactData: { alias: "newAlias" } },
                })
            })

            it("should do nothing if state is not finished", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "PENDING",
                        params: exampleUserId,
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual(state.publicData)
            })

            it("should do nothing if data fetched for another user", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "FINISHED",
                        params: "another-user-id",
                        data: { userId: exampleUserId, nickName: "oldNickName", contactData: null },
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual(state.publicData)
            })

            it("should do nothing if fetched data is empty", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "FINISHED",
                        params: exampleUserId,
                        data: null,
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual(state.publicData)
            })
        })

        describe("on contact delete finished", () => {
            const action = deleteContactAction.done({ params: exampleUserId })
            it("should set contact data to null", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "FINISHED",
                        params: exampleUserId,
                        data: { userId: exampleUserId, nickName: exampleUserNickname, contactData: null },
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual({
                    status: "FINISHED",
                    params: exampleUserId,
                    data: { userId: exampleUserId, nickName: exampleUserNickname, contactData: null },
                })
            })

            it("should do nothing if state is not finished", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "PENDING",
                        params: exampleUserId,
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual(state.publicData)
            })

            it("should do nothing if data fetched for another user", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "FINISHED",
                        params: "another-user-id",
                        data: { userId: exampleUserId, nickName: "oldNickName", contactData: null },
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual(state.publicData)
            })

            it("should do nothing if fetched data is empty", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "FINISHED",
                        params: exampleUserId,
                        data: null,
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual(state.publicData)
            })
        })

        describe("on contact edit finished", () => {
            const action = editContactAction.done({
                params: { contactId: exampleUserId, data: { alias: { value: "new-alias" } } },
                result: { userId: exampleUserId, nickName: exampleUserNickname, alias: "new-alias" },
            })

            it("should be updated", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "FINISHED",
                        params: exampleUserId,
                        data: { userId: exampleUserId, nickName: exampleUserNickname, contactData: null },
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual({
                    status: "FINISHED",
                    params: exampleUserId,
                    data: { userId: exampleUserId, nickName: exampleUserNickname, contactData: { alias: "new-alias" } },
                })
            })

            it("should do nothing if contact data is not fetched yet", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "PENDING",
                        params: exampleUserId,
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual(state.publicData)
            })

            it("should do nothing if user id does not match", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "FINISHED",
                        params: "other-user-id",
                        data: { userId: "other-user-id", nickName: exampleUserNickname, contactData: null },
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual(state.publicData)
            })

            it("should do nothing is user data is not set", () => {
                const state: ReturnType<typeof usersReducer> = {
                    ...defaultState,
                    publicData: {
                        status: "FINISHED",
                        params: exampleUserId,
                        data: null,
                    },
                }
                const result = usersReducer(state, action)
                expect(result.publicData).toStrictEqual(state.publicData)
            })
        })
    })

    describe("contacts filter", () => {
        const state: ReturnType<typeof usersReducer> = {
            ...defaultState,
            contactsFilter: { name: "foo" },
        }

        describe("name filter", () => {
            it("should be updated with provided value", () => {
                const action = updateContactsFilterAction({ name: "bar" })
                const result = usersReducer(state, action)
                expect(result.contactsFilter).toStrictEqual({ name: "bar" })
            })

            it("should not be updated if value is missing", () => {
                const action = updateContactsFilterAction({ name: undefined })
                const result = usersReducer(state, action)
                expect(result.contactsFilter).toStrictEqual({ name: "foo" })
            })
        })
    })

    describe("requested contact to delete", () => {
        it("should be updated", () => {
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                contactRequestedToDelete: { userId: exampleUserId, nickName: exampleUserNickname, alias: "a1" },
            }
            const action = requestContactDeleteAction({ userId: "newUserId", nickName: "newNickname" })
            const result = usersReducer(state, action)
            expect(result.contactRequestedToDelete).toStrictEqual({ userId: "newUserId", nickName: "newNickname" })
        })
    })

    describe("requested contact to edit", () => {
        it("should be updated", () => {
            const state: ReturnType<typeof usersReducer> = {
                ...defaultState,
                contactRequestedToEdit: null,
            }
            const action = requestContactEditAction({
                userId: exampleUserId,
                nickName: exampleUserNickname,
                alias: exampleContactAlias,
            })
            const result = usersReducer(state, action)
            expect(result.contactRequestedToEdit).toStrictEqual({
                userId: exampleUserId,
                nickName: exampleUserNickname,
                alias: exampleContactAlias,
            })
        })
    })
})
