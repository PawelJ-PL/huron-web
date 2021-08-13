import { rest } from "msw"
import { setupServer } from "msw/node"
import CollectionsApi from "./CollectionsApi"

describe("Collections api", () => {
    describe("fetch collection", () => {
        it("should return null on 400", async () => {
            const server = setupServer(
                rest.get("http://127.0.0.1:8080/api/v1/collections/foo", (_, res, ctx) => {
                    return res(ctx.status(400))
                })
            )
            server.listen()

            const result = await CollectionsApi.fetchCollection("foo")
            expect(result).toBe(null)

            server.close()
        })

        it("should return null on 403", async () => {
            const server = setupServer(
                rest.get("http://127.0.0.1:8080/api/v1/collections/foo", (_, res, ctx) => {
                    return res(ctx.status(403))
                })
            )
            server.listen()

            const result = await CollectionsApi.fetchCollection("foo")
            expect(result).toBe(null)

            server.close()
        })

        it("should return null on 404", async () => {
            const server = setupServer(
                rest.get("http://127.0.0.1:8080/api/v1/collections/foo", (_, res, ctx) => {
                    return res(ctx.status(404))
                })
            )
            server.listen()

            const result = await CollectionsApi.fetchCollection("foo")
            expect(result).toBe(null)

            server.close()
        })
    })

    describe("fetch collection key", () => {
        it("should return null on 403", async () => {
            const server = setupServer(
                rest.get("http://127.0.0.1:8080/api/v1/collections/foo/encryption-key", (_, res, ctx) => {
                    return res(ctx.status(403))
                })
            )
            server.listen()

            const result = await CollectionsApi.fetchEncryptionKey("foo")
            expect(result).toBe(null)

            server.close()
        })

        it("should return null on 404", async () => {
            const server = setupServer(
                rest.get("http://127.0.0.1:8080/api/v1/collections/foo/encryption-key", (_, res, ctx) => {
                    return res(ctx.status(404))
                })
            )
            server.listen()

            const result = await CollectionsApi.fetchEncryptionKey("foo")
            expect(result).toBe(null)

            server.close()
        })
    })
})
