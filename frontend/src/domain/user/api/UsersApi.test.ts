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
import {
    exampleEncryptedPrivateKey,
    exampleHashedEmail,
    examplePublicKey,
    exampleUserEmail,
    exampleUserId,
    exampleUserNickname,
    exampleUserPassword,
} from "./../../../testutils/constants/user"
import UsersApi from "./UsersApi"
import { rest } from "msw"
import { setupServer } from "msw/node"
import { assertHttpErrorWithStatusCode } from "../../../testutils/assertions"

describe("Users api", () => {
    describe("login", () => {
        it("should return user data", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users/auth/login", (_, res, ctx) => {
                    return res(
                        ctx.json({
                            id: exampleUserId,
                            nickName: exampleUserNickname,
                            language: "Pl",
                            emailHash: exampleHashedEmail,
                        }),
                        ctx.set("X-Csrf-Token", "ABCDEFGHIJK")
                    )
                })
            )
            server.listen()

            const result = UsersApi.login(exampleUserEmail, exampleUserPassword)
            await expect(result).resolves.toEqual({
                csrfToken: "ABCDEFGHIJK",
                userData: {
                    id: exampleUserId,
                    language: "Pl",
                    nickName: exampleUserNickname,
                    emailHash: exampleHashedEmail,
                },
            })

            server.close()
        })

        it("should return return null on 401", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users/auth/login", (_, res, ctx) => {
                    return res(ctx.status(401))
                })
            )
            server.listen()

            const result = UsersApi.login(exampleUserEmail, "invalidPassword")
            await expect(result).resolves.toBeNull()

            server.close()
        })

        it("should return return null on 400", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users/auth/login", (_, res, ctx) => {
                    return res(ctx.status(400))
                })
            )
            server.listen()

            const result = UsersApi.login(exampleUserEmail, "invalidPassword")
            await expect(result).resolves.toBeNull()

            server.close()
        })

        it("should return return original error", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users/auth/login", (_, res, ctx) => {
                    return res(ctx.status(500))
                })
            )
            server.listen()

            const result = UsersApi.login(exampleUserEmail, "invalidPassword")
            await assertHttpErrorWithStatusCode(result, 500)

            server.close()
        })
    })

    describe("register user", () => {
        it("should return void on success", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users", (_, res, ctx) => {
                    return res(ctx.status(201))
                })
            )
            server.listen()

            const result = UsersApi.registerUser(
                exampleUserNickname,
                exampleUserEmail,
                exampleUserPassword,
                { algorithm: "Rsa", publicKey: examplePublicKey, encryptedPrivateKey: exampleEncryptedPrivateKey },
                "Pl"
            )
            await expect(result).resolves.toBeUndefined()

            server.close()
        })

        it("should set error EmailAlreadyRegistered on 409 with EmailAlreadyRegistered reason", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users", (_, res, ctx) => {
                    return res(ctx.status(409), ctx.json({ reason: "EmailAlreadyRegistered" }))
                })
            )
            server.listen()

            const result = UsersApi.registerUser(
                exampleUserNickname,
                exampleUserEmail,
                exampleUserPassword,
                { algorithm: "Rsa", publicKey: examplePublicKey, encryptedPrivateKey: exampleEncryptedPrivateKey },
                "Pl"
            )
            await expect(result).rejects.toEqual(new EmailAlreadyRegistered(exampleUserEmail))

            server.close()
        })

        it("should set error NicknameAlreadyRegistered on 409 with NickNameAlreadyRegistered reason", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users", (_, res, ctx) => {
                    return res(ctx.status(409), ctx.json({ reason: "NickNameAlreadyRegistered" }))
                })
            )
            server.listen()

            const result = UsersApi.registerUser(
                exampleUserNickname,
                exampleUserEmail,
                exampleUserPassword,
                { algorithm: "Rsa", publicKey: examplePublicKey, encryptedPrivateKey: exampleEncryptedPrivateKey },
                "Pl"
            )
            await expect(result).rejects.toEqual(new NicknameAlreadyRegistered(exampleUserNickname))

            server.close()
        })

        it("should set standard error on other response", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users", (_, res, ctx) => {
                    return res(ctx.status(500))
                })
            )
            server.listen()

            const result = UsersApi.registerUser(
                exampleUserNickname,
                exampleUserEmail,
                exampleUserPassword,
                { algorithm: "Rsa", publicKey: examplePublicKey, encryptedPrivateKey: exampleEncryptedPrivateKey },
                "Pl"
            )
            await assertHttpErrorWithStatusCode(result, 500)
            server.close()
        })
    })

    describe("activate account", () => {
        it("should return true", async () => {
            const server = setupServer(
                rest.get("http://127.0.0.1:8080/api/v1/users/registrations/X-Y-Z", (_, res, ctx) => {
                    return res(ctx.status(201))
                })
            )
            server.listen()

            const result = UsersApi.activateAccount("X-Y-Z")
            await expect(result).resolves.toBe(true)

            server.close()
        })

        it("should return true false on 404", async () => {
            const server = setupServer(
                rest.get("http://127.0.0.1:8080/api/v1/users/registrations/X-Y-Z", (_, res, ctx) => {
                    return res(ctx.status(404))
                })
            )
            server.listen()

            const result = UsersApi.activateAccount("X-Y-Z")
            await expect(result).resolves.toBe(false)

            server.close()
        })

        it("should return original error", async () => {
            const server = setupServer(
                rest.get("http://127.0.0.1:8080/api/v1/users/registrations/X-Y-Z", (_, res, ctx) => {
                    return res(ctx.status(500))
                })
            )
            server.listen()

            const result = UsersApi.activateAccount("X-Y-Z")
            await assertHttpErrorWithStatusCode(result, 500)

            server.close()
        })
    })

    describe("reset password", () => {
        it("should return true", async () => {
            const server = setupServer(
                rest.put("http://127.0.0.1:8080/api/v1/users/password/X-Y-Z", (_, res, ctx) => {
                    return res(ctx.status(201))
                })
            )
            server.listen()

            const result = UsersApi.resetPassword(
                "X-Y-Z",
                "newPassword",
                { algorithm: "Rsa", publicKey: examplePublicKey, encryptedPrivateKey: exampleEncryptedPrivateKey },
                exampleUserEmail
            )
            await expect(result).resolves.toBe(true)

            server.close()
        })

        it("should return false on 404", async () => {
            const server = setupServer(
                rest.put("http://127.0.0.1:8080/api/v1/users/password/X-Y-Z", (_, res, ctx) => {
                    return res(ctx.status(404))
                })
            )
            server.listen()

            const result = UsersApi.resetPassword(
                "X-Y-Z",
                "newPassword",
                { algorithm: "Rsa", publicKey: examplePublicKey, encryptedPrivateKey: exampleEncryptedPrivateKey },
                exampleUserEmail
            )
            await expect(result).resolves.toBe(false)

            server.close()
        })

        it("should return origin error", async () => {
            const server = setupServer(
                rest.put("http://127.0.0.1:8080/api/v1/users/password/X-Y-Z", (_, res, ctx) => {
                    return res(ctx.status(500))
                })
            )
            server.listen()

            const result = UsersApi.resetPassword(
                "X-Y-Z",
                "newPassword",
                { algorithm: "Rsa", publicKey: examplePublicKey, encryptedPrivateKey: exampleEncryptedPrivateKey },
                exampleUserEmail
            )
            await assertHttpErrorWithStatusCode(result, 500)

            server.close()
        })
    })

    describe("change password", () => {
        it("should return void", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users/me/password", (_, res, ctx) => {
                    return res(ctx.status(201))
                })
            )
            server.listen()

            const result = UsersApi.changePassword({
                email: exampleUserEmail,
                currentPassword: exampleUserPassword,
                newPassword: "newPassword",
                keyPair: {
                    algorithm: "Rsa",
                    publicKey: examplePublicKey,
                    encryptedPrivateKey: exampleEncryptedPrivateKey,
                },
                collectionEncryptionKeys: [],
            })
            await expect(result).resolves.toBeUndefined()

            server.close()
        })

        it("should set passwords are equal error", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users/me/password", (_, res, ctx) => {
                    return res(ctx.status(412), ctx.json({ reason: "PasswordsEqual" }))
                })
            )
            server.listen()

            const result = UsersApi.changePassword({
                email: exampleUserEmail,
                currentPassword: exampleUserPassword,
                newPassword: exampleUserPassword,
                keyPair: {
                    algorithm: "Rsa",
                    publicKey: examplePublicKey,
                    encryptedPrivateKey: exampleEncryptedPrivateKey,
                },
                collectionEncryptionKeys: [],
            })
            await expect(result).rejects.toEqual(new PasswordsAreEqual())

            server.close()
        })

        it("should set invalid credentials error", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users/me/password", (_, res, ctx) => {
                    return res(ctx.status(412), ctx.json({ reason: "InvalidCurrentPassword" }))
                })
            )
            server.listen()

            const result = UsersApi.changePassword({
                email: exampleUserEmail,
                currentPassword: exampleUserPassword,
                newPassword: exampleUserPassword,
                keyPair: {
                    algorithm: "Rsa",
                    publicKey: examplePublicKey,
                    encryptedPrivateKey: exampleEncryptedPrivateKey,
                },
                collectionEncryptionKeys: [],
            })
            await expect(result).rejects.toEqual(new InvalidCredentials())

            server.close()
        })

        it("should set invalid email error", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users/me/password", (_, res, ctx) => {
                    return res(ctx.status(412), ctx.json({ reason: "InvalidEmail" }))
                })
            )
            server.listen()

            const result = UsersApi.changePassword({
                email: exampleUserEmail,
                currentPassword: exampleUserPassword,
                newPassword: exampleUserPassword,
                keyPair: {
                    algorithm: "Rsa",
                    publicKey: examplePublicKey,
                    encryptedPrivateKey: exampleEncryptedPrivateKey,
                },
                collectionEncryptionKeys: [],
            })
            await expect(result).rejects.toEqual(new InvalidEmail())

            server.close()
        })

        it("should return original error", async () => {
            const server = setupServer(
                rest.post("http://127.0.0.1:8080/api/v1/users/me/password", (_, res, ctx) => {
                    return res(ctx.status(500))
                })
            )
            server.listen()

            const result = UsersApi.changePassword({
                email: exampleUserEmail,
                currentPassword: exampleUserPassword,
                newPassword: "newPassword",
                keyPair: {
                    algorithm: "Rsa",
                    publicKey: examplePublicKey,
                    encryptedPrivateKey: exampleEncryptedPrivateKey,
                },
                collectionEncryptionKeys: [],
            })
            await assertHttpErrorWithStatusCode(result, 500)

            server.close()
        })
    })

    describe("update password", () => {
        it("should set proper error on nickname conflict", async () => {
            const server = setupServer(
                rest.patch("http://127.0.0.1:8080/api/v1/users/me/data", (_, res, ctx) => {
                    return res(ctx.status(409), ctx.json({ reason: "NickNameAlreadyRegistered" }))
                })
            )
            server.listen()

            const result = UsersApi.updateProfile({ nickName: exampleUserNickname })

            await expect(result).rejects.toEqual(new NicknameAlreadyRegistered(exampleUserNickname))

            server.close()
        })
    })

    describe("fetch public data of user", () => {
        it("should return null on 404", async () => {
            const server = setupServer(
                rest.get(`http://127.0.0.1:8080/api/v1/users/${exampleUserId}/data`, (_, res, ctx) => {
                    return res(ctx.status(404))
                })
            )
            server.listen()

            const result = await UsersApi.fetchUserPublicData(exampleUserId)

            expect(result).toBeNull()
        })

        it("should return null on 400", async () => {
            const server = setupServer(
                rest.get(`http://127.0.0.1:8080/api/v1/users/${exampleUserId}/data`, (_, res, ctx) => {
                    return res(ctx.status(400))
                })
            )
            server.listen()

            const result = await UsersApi.fetchUserPublicData(exampleUserId)

            expect(result).toBeNull()
        })
    })

    describe("fetch multiple users data", () => {
        it("should return error if too many users requested", async () => {
            const userIds = Array.from(Array(21).keys()).map((n) => n.toString(10))
            const result = UsersApi.fetchMultipleUsersPublicData(userIds)
            await expect(result).rejects.toEqual(new TooManyUsersToFetch())
        })
    })

    describe("create contact", () => {
        it("should return error on alias conflict", async () => {
            const server = setupServer(
                rest.post(`http://127.0.0.1:8080/api/v1/users/me/contacts`, (_, res, ctx) => {
                    return res(ctx.status(409), ctx.json({ reason: "ContactAliasAlreadyExists" }))
                })
            )
            server.listen()

            const result = UsersApi.createContact(exampleUserId, exampleUserNickname)

            await expect(result).rejects.toEqual(new ContactWithAliasAlreadyExists(exampleUserNickname))
        })

        it("should return error on userId conflict", async () => {
            const server = setupServer(
                rest.post(`http://127.0.0.1:8080/api/v1/users/me/contacts`, (_, res, ctx) => {
                    return res(ctx.status(409), ctx.json({ reason: "ContactAlreadyExists" }))
                })
            )
            server.listen()

            const result = UsersApi.createContact(exampleUserId, exampleUserNickname)

            await expect(result).rejects.toEqual(new UserAlreadyInContacts(exampleUserId))
        })

        it("should return error on adding self", async () => {
            const server = setupServer(
                rest.post(`http://127.0.0.1:8080/api/v1/users/me/contacts`, (_, res, ctx) => {
                    return res(ctx.status(412), ctx.json({ reason: "AddSelfToContacts" }))
                })
            )
            server.listen()

            const result = UsersApi.createContact(exampleUserId, exampleUserNickname)

            await expect(result).rejects.toEqual(new AddSelfToContacts())
        })
    })

    describe("edit contact", () => {
        it("should return error on contact alias conflict", async () => {
            const server = setupServer(
                rest.post(`http://127.0.0.1:8080/api/v1/users/me/contacts`, (_, res, ctx) => {
                    return res(ctx.status(409), ctx.json({ reason: "ContactAliasAlreadyExists" }))
                })
            )
            server.listen()

            const result = UsersApi.createContact(exampleUserId, exampleUserNickname)

            await expect(result).rejects.toEqual(new ContactWithAliasAlreadyExists(exampleUserNickname))
        })
    })

    describe("find user by nickName", () => {
        it("Should return error if query is too short", async () => {
            const query = "foo"

            const result = UsersApi.findUserByNickName({ nickNameStart: query })

            await expect(result).rejects.toEqual(new NickNameQueryTooShort(query))
        })
    })
})
