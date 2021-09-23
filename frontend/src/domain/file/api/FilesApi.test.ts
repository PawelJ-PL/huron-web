import { exampleEncryptionKey } from "./../../../testutils/constants/collection"
import {
    exampleDirectoryData,
    exampleDirectoryId,
    exampleFileData,
    exampleFileId,
} from "./../../../testutils/constants/files"
import { rest } from "msw"
import { setupServer } from "msw/node"
import { exampleCollectionId } from "../../../testutils/constants/collection"
import FilesApi from "./FilesApi"
import { FileAlreadyExists, FileContentNotChanged, FileNotFound, NotADirectory, RecursivelyDelete } from "./errors"

describe("Files API", () => {
    describe("get metadata", () => {
        it("should return FileNotFound error on 400", async () => {
            const server = setupServer(
                rest.get(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleFileId}/metadata`,
                    (_, res, ctx) => {
                        return res(ctx.status(400))
                    }
                )
            )
            server.listen()

            const result = FilesApi.getMetadata(exampleCollectionId, exampleFileId)
            await expect(result).rejects.toEqual(new FileNotFound(exampleFileId, exampleCollectionId))

            server.close()
        })

        it("should return FileNotFound error on 403", async () => {
            const server = setupServer(
                rest.get(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleFileId}/metadata`,
                    (_, res, ctx) => {
                        return res(ctx.status(403))
                    }
                )
            )
            server.listen()

            const result = FilesApi.getMetadata(exampleCollectionId, exampleFileId)
            await expect(result).rejects.toEqual(new FileNotFound(exampleFileId, exampleCollectionId))

            server.close()
        })

        it("should return FileNotFound error on 404", async () => {
            const server = setupServer(
                rest.get(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleFileId}/metadata`,
                    (_, res, ctx) => {
                        return res(ctx.status(404))
                    }
                )
            )
            server.listen()

            const result = FilesApi.getMetadata(exampleCollectionId, exampleFileId)
            await expect(result).rejects.toEqual(new FileNotFound(exampleFileId, exampleCollectionId))

            server.close()
        })
    })

    describe("get parents", () => {
        it("should return FileNotFound error on 400", async () => {
            const server = setupServer(
                rest.get(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleFileId}/parents`,
                    (_, res, ctx) => {
                        return res(ctx.status(400))
                    }
                )
            )
            server.listen()

            const result = FilesApi.getParents(exampleCollectionId, exampleFileId)
            await expect(result).rejects.toEqual(new FileNotFound(exampleFileId, exampleCollectionId))

            server.close()
        })

        it("should return FileNotFound error on 403", async () => {
            const server = setupServer(
                rest.get(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleFileId}/parents`,
                    (_, res, ctx) => {
                        return res(ctx.status(403))
                    }
                )
            )
            server.listen()

            const result = FilesApi.getParents(exampleCollectionId, exampleFileId)
            await expect(result).rejects.toEqual(new FileNotFound(exampleFileId, exampleCollectionId))

            server.close()
        })

        it("should return FileNotFound error on 404", async () => {
            const server = setupServer(
                rest.get(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleFileId}/parents`,
                    (_, res, ctx) => {
                        return res(ctx.status(404))
                    }
                )
            )
            server.listen()

            const result = FilesApi.getParents(exampleCollectionId, exampleFileId)
            await expect(result).rejects.toEqual(new FileNotFound(exampleFileId, exampleCollectionId))

            server.close()
        })
    })

    describe("get children", () => {
        it("should return FileNotFound error on 400", async () => {
            const server = setupServer(
                rest.get(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleDirectoryId}/children`,
                    (_, res, ctx) => {
                        return res(ctx.status(400))
                    }
                )
            )
            server.listen()

            const result = FilesApi.getChildren(exampleCollectionId, exampleDirectoryId)
            await expect(result).rejects.toEqual(new FileNotFound(exampleDirectoryId, exampleCollectionId))

            server.close()
        })

        it("should return FileNotFound error on 403", async () => {
            const server = setupServer(
                rest.get(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleDirectoryId}/children`,
                    (_, res, ctx) => {
                        return res(ctx.status(403))
                    }
                )
            )
            server.listen()

            const result = FilesApi.getChildren(exampleCollectionId, exampleDirectoryId)
            await expect(result).rejects.toEqual(new FileNotFound(exampleDirectoryId, exampleCollectionId))

            server.close()
        })

        it("should return FileNotFound error on 404", async () => {
            const server = setupServer(
                rest.get(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleDirectoryId}/children`,
                    (_, res, ctx) => {
                        return res(ctx.status(404))
                    }
                )
            )
            server.listen()

            const result = FilesApi.getChildren(exampleCollectionId, exampleDirectoryId)
            await expect(result).rejects.toEqual(new FileNotFound(exampleDirectoryId, exampleCollectionId))

            server.close()
        })

        it("should return NotADirectoryError error", async () => {
            const server = setupServer(
                rest.get(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleDirectoryId}/children`,
                    (_, res, ctx) => {
                        return res(ctx.status(412), ctx.json({ reason: "NotADirectory" }))
                    }
                )
            )
            server.listen()

            const result = FilesApi.getChildren(exampleCollectionId, exampleDirectoryId)
            await expect(result).rejects.toEqual(new NotADirectory(exampleDirectoryId))

            server.close()
        })
    })

    describe("create directory", () => {
        it("should return FileAlreadyExists error", async () => {
            const server = setupServer(
                rest.post(`http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files`, (_, res, ctx) => {
                    return res(ctx.status(409))
                })
            )
            server.listen()

            const result = FilesApi.createDirectory({
                parent: null,
                collectionId: exampleCollectionId,
                name: exampleDirectoryData.name,
            })
            await expect(result).rejects.toEqual(
                new FileAlreadyExists(exampleCollectionId, null, exampleDirectoryData.name)
            )

            server.close()
        })
    })

    describe("upload file", () => {
        it("should return FileAlreadyExists error", async () => {
            const server = setupServer(
                rest.post(`http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files`, (_, res, ctx) => {
                    return res(ctx.status(409))
                })
            )
            server.listen()

            const result = FilesApi.uploadFile({
                collectionId: exampleCollectionId,
                parent: null,
                name: exampleFileData.name,
                contentDigest: exampleFileData.contentDigest,
                content: {
                    algorithm: "AES-CBC",
                    iv: "010203",
                    encryptionKeyVersion: exampleEncryptionKey.version,
                    bytes: "666768",
                },
            })
            await expect(result).rejects.toEqual(new FileAlreadyExists(exampleCollectionId, null, exampleFileData.name))

            server.close()
        })
    })

    describe("edit metadata", () => {
        it("should return FileAlreadyExists error", async () => {
            const server = setupServer(
                rest.patch(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleFileId}`,
                    (_, res, ctx) => {
                        return res(ctx.status(409))
                    }
                )
            )
            server.listen()

            const result = FilesApi.editMetadata(exampleFileId, exampleCollectionId, { name: "new-name" })
            await expect(result).rejects.toEqual(new FileAlreadyExists(exampleCollectionId, "unknown", "new-name"))

            server.close()
        })
    })

    describe("delete file", () => {
        it("should return RecursivelyDelete error", async () => {
            const server = setupServer(
                rest.delete(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleDirectoryId}`,
                    (_, res, ctx) => {
                        return res(ctx.status(412), ctx.json({ reason: "RecursivelyDelete" }))
                    }
                )
            )
            server.listen()

            const result = FilesApi.deleteFile(exampleCollectionId, exampleDirectoryId)
            await expect(result).rejects.toEqual(new RecursivelyDelete(exampleCollectionId, exampleDirectoryId))

            server.close()
        })
    })

    describe("upload version", () => {
        it("should return FileContentNotChanged error", async () => {
            const server = setupServer(
                rest.post(
                    `http://127.0.0.1:8080/api/v1/collections/${exampleCollectionId}/files/${exampleFileId}/versions`,
                    (_, res, ctx) => {
                        return res(ctx.status(422), ctx.json({ reason: "FileContentNotChanged" }))
                    }
                )
            )
            server.listen()

            const result = FilesApi.uploadVersion({
                collectionId: exampleCollectionId,
                fileId: exampleFileId,
                contentDigest: exampleFileData.contentDigest,
                content: {
                    algorithm: "AES-CBC",
                    iv: "010203",
                    encryptionKeyVersion: exampleEncryptionKey.version,
                    bytes: "666768",
                },
            })
            await expect(result).rejects.toEqual(new FileContentNotChanged(exampleCollectionId, exampleFileId))

            server.close()
        })
    })
})
