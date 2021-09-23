import { FileContentNotChanged } from "./../api/errors"
import { FileMetadata } from "./../types/FilesystemUnitMetadata"
import { FileDeleteFailed } from "./../types/errors"
import {
    exampleChildDirectory1,
    exampleChildDirectory2,
    exampleChildFile1,
    exampleDirectoryData,
    exampleFileContent,
    exampleFileName,
    exampleVersionId,
} from "./../../../testutils/constants/files"
import { exampleCollectionId, exampleEncryptionKey } from "./../../../testutils/constants/collection"
import { AppState } from "./../../../application/store/index"
import {
    deleteFilesAction,
    DeleteFilesParams,
    downloadAndDecryptFileAction,
    DownloadFileParams,
    encryptAndUpdateVersionAction,
    encryptAndUploadFileAction,
    fetchObjectTreeAction,
    NewFileData,
    NewVersionData,
} from "./Actions"
import { verifyAsyncEpic } from "../../../testutils/epicsUtils"
import { filesEpics } from "./Epics"
import { exampleFileData } from "../../../testutils/constants/files"
import FilesApi from "../api/FilesApi"
import CryptoApi from "../../../application/cryptography/api/CryptoApi"
import * as forge from "node-forge"
import { TextEncoder } from "util"

const defaultState: AppState = {} as AppState

const fileContentWithArrayBuffer: File = {
    ...exampleFileContent,
    arrayBuffer: () => Promise.resolve(new Uint8Array([122, 123, 124, 125])),
    name: exampleFileName,
    type: "application/pdf",
}

describe("Files epics", () => {
    beforeEach(() => {
        jest.spyOn(CryptoApi, "encryptBinary").mockImplementation((input, keyHex) =>
            Promise.resolve(`AES-CBC:someIv:encrypted(${input}, ${keyHex})`)
        )

        jest.spyOn(CryptoApi, "digest").mockImplementation((input) => Promise.resolve(`digest(${input})`))

        jest.spyOn(forge.util, "createBuffer").mockImplementation(
            (input) => new forge.util.ByteStringBuffer(input.toString())
        )

        jest.spyOn(CryptoApi, "decryptBinary").mockImplementation((encryptedInput: string, keyHex: string) => {
            const content = `decrypted(${encryptedInput}---${keyHex})`
            const encoder = new TextEncoder()
            return Promise.resolve(encoder.encode(content))
        })
    })

    afterEach(() => {
        jest.restoreAllMocks()
    })

    describe("fetch tree", () => {
        it("should trigger fetch root directory tree if object ID is null", async () => {
            const getChildrenSpy = jest.spyOn(FilesApi, "getChildren").mockResolvedValue([exampleFileData])

            const params = { collectionId: exampleCollectionId, objectId: null }

            const trigger = fetchObjectTreeAction.started(params)
            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                fetchObjectTreeAction.done({ params, result: { children: [exampleFileData] } })
            )

            expect(getChildrenSpy).toHaveBeenCalledTimes(1)
            expect(getChildrenSpy).toHaveBeenCalledWith(exampleCollectionId, null)
        })

        it("should trigger fetch file tree", async () => {
            const getMetadataSpy = jest
                .spyOn(FilesApi, "getMetadata")
                .mockResolvedValue({ ...exampleFileData, parent: exampleChildDirectory1.id })
            const getParentSpy = jest
                .spyOn(FilesApi, "getParents")
                .mockResolvedValue([exampleChildDirectory1, exampleChildDirectory2])

            const params = { collectionId: exampleCollectionId, objectId: exampleFileData.id }

            const trigger = fetchObjectTreeAction.started(params)
            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                fetchObjectTreeAction.done({
                    params,
                    result: {
                        metadata: { ...exampleFileData, parent: exampleChildDirectory1.id },
                        parents: [exampleChildDirectory1, exampleChildDirectory2],
                    },
                })
            )

            expect(getMetadataSpy).toHaveBeenCalledTimes(1)
            expect(getMetadataSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
            expect(getParentSpy).toHaveBeenCalledTimes(1)
            expect(getParentSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
        })

        it("should trigger fetch file tree without parents", async () => {
            const getMetadataSpy = jest
                .spyOn(FilesApi, "getMetadata")
                .mockResolvedValue({ ...exampleFileData, parent: null })

            const params = { collectionId: exampleCollectionId, objectId: exampleFileData.id }

            const trigger = fetchObjectTreeAction.started(params)
            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                fetchObjectTreeAction.done({
                    params,
                    result: {
                        metadata: { ...exampleFileData, parent: null },
                        parents: [],
                    },
                })
            )

            expect(getMetadataSpy).toHaveBeenCalledTimes(1)
            expect(getMetadataSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
        })

        it("should trigger fetch directory tree", async () => {
            const getMetadataSpy = jest
                .spyOn(FilesApi, "getMetadata")
                .mockResolvedValue({ ...exampleDirectoryData, parent: exampleChildDirectory1.id })
            const getParentSpy = jest.spyOn(FilesApi, "getParents").mockResolvedValue([exampleChildDirectory1])
            const getChildrenSpy = jest
                .spyOn(FilesApi, "getChildren")
                .mockResolvedValue([exampleChildDirectory2, exampleChildFile1])

            const params = { collectionId: exampleCollectionId, objectId: exampleFileData.id }

            const trigger = fetchObjectTreeAction.started(params)
            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                fetchObjectTreeAction.done({
                    params,
                    result: {
                        metadata: { ...exampleDirectoryData, parent: exampleChildDirectory1.id },
                        parents: [exampleChildDirectory1],
                        children: [exampleChildDirectory2, exampleChildFile1],
                    },
                })
            )

            expect(getMetadataSpy).toHaveBeenCalledTimes(1)
            expect(getMetadataSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
            expect(getParentSpy).toHaveBeenCalledTimes(1)
            expect(getParentSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
            expect(getChildrenSpy).toHaveBeenCalledTimes(1)
            expect(getChildrenSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
        })

        it("should trigger fetch directory tree without parents", async () => {
            const getMetadataSpy = jest
                .spyOn(FilesApi, "getMetadata")
                .mockResolvedValue({ ...exampleDirectoryData, parent: null })
            const getChildrenSpy = jest
                .spyOn(FilesApi, "getChildren")
                .mockResolvedValue([exampleChildDirectory2, exampleChildFile1])

            const params = { collectionId: exampleCollectionId, objectId: exampleFileData.id }

            const trigger = fetchObjectTreeAction.started(params)
            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                fetchObjectTreeAction.done({
                    params,
                    result: {
                        metadata: { ...exampleDirectoryData, parent: null },
                        parents: [],
                        children: [exampleChildDirectory2, exampleChildFile1],
                    },
                })
            )

            expect(getMetadataSpy).toHaveBeenCalledTimes(1)
            expect(getMetadataSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
            expect(getChildrenSpy).toHaveBeenCalledTimes(1)
            expect(getChildrenSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
        })
    })

    describe("encrypt and upload file", () => {
        const params: NewFileData = {
            collectionId: exampleCollectionId,
            parent: exampleDirectoryData.id,
            file: fileContentWithArrayBuffer,
            encryptionKey: exampleEncryptionKey,
        }

        it("should trigger encrypt and upload file", async () => {
            const uploadSpy = jest.spyOn(FilesApi, "uploadFile").mockResolvedValue(exampleFileData)

            const trigger = encryptAndUploadFileAction.started(params)
            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                encryptAndUploadFileAction.done({ params, result: exampleFileData })
            )

            expect(uploadSpy).toHaveBeenCalledTimes(1)
            expect(uploadSpy).toHaveBeenCalledWith({
                collectionId: exampleCollectionId,
                content: {
                    algorithm: "AES-CBC",
                    bytes: "encrypted(122,123,124,125, AES-CBC",
                    encryptionKeyVersion: exampleEncryptionKey.version,
                    iv: "someIv",
                },
                contentDigest: "digest(3132322c3132332c3132342c313235)",
                mimeType: "application/pdf",
                name: exampleFileData.name,
                parent: params.parent,
            })
        })

        it("should not trigger encrypt and upload file if provied encryption key from another collection", async () => {
            const triggerParams = {
                ...params,
                encryptionKey: { ...params.encryptionKey, collectionId: "another-collection" },
            }

            const trigger = encryptAndUploadFileAction.started(triggerParams)
            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                encryptAndUploadFileAction.failed({
                    params: triggerParams,
                    error: new Error(
                        `Provided encryption key of collection ${triggerParams.encryptionKey.collectionId} instead of ${exampleCollectionId}`
                    ),
                })
            )
        })
    })

    describe("delete files", () => {
        const params: DeleteFilesParams = {
            collectionId: exampleCollectionId,
            fileIds: [exampleFileData.id, exampleChildFile1.id, exampleChildDirectory1.id],
        }

        it("should trigger files delete", async () => {
            const deleteFileSpy = jest.spyOn(FilesApi, "deleteFile").mockResolvedValue()

            const trigger = deleteFilesAction.started(params)

            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                deleteFilesAction.done({ params, result: undefined })
            )

            expect(deleteFileSpy).toHaveBeenCalledTimes(3)
            expect(deleteFileSpy).toHaveBeenNthCalledWith(1, exampleCollectionId, exampleFileData.id, undefined)
            expect(deleteFileSpy).toHaveBeenNthCalledWith(2, exampleCollectionId, exampleChildFile1.id, undefined)
            expect(deleteFileSpy).toHaveBeenNthCalledWith(3, exampleCollectionId, exampleChildDirectory1.id, undefined)
        })

        it("should trigger files delete recursively", async () => {
            const deleteFileSpy = jest.spyOn(FilesApi, "deleteFile").mockResolvedValue()

            const trigger = deleteFilesAction.started({ ...params, deleteNonEmpty: true })

            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                deleteFilesAction.done({ params: { ...params, deleteNonEmpty: true }, result: undefined })
            )

            expect(deleteFileSpy).toHaveBeenCalledTimes(3)
            expect(deleteFileSpy).toHaveBeenNthCalledWith(1, exampleCollectionId, exampleFileData.id, true)
            expect(deleteFileSpy).toHaveBeenNthCalledWith(2, exampleCollectionId, exampleChildFile1.id, true)
            expect(deleteFileSpy).toHaveBeenNthCalledWith(3, exampleCollectionId, exampleChildDirectory1.id, true)
        })

        it("should trigger files delete with errors", async () => {
            const deleteFileSpy = jest.spyOn(FilesApi, "deleteFile").mockImplementation((_, fileId) => {
                if (fileId === exampleChildFile1.id) {
                    return Promise.reject(new Error("Some error"))
                } else {
                    return Promise.resolve()
                }
            })

            const trigger = deleteFilesAction.started(params)

            const resultAction = await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                deleteFilesAction.failed({
                    params,
                    error: new FileDeleteFailed(
                        [exampleFileData.id, exampleChildDirectory1.id],
                        [{ fileId: exampleChildFile1.id, error: new Error("Some error") }]
                    ),
                })
            )

            expect(deleteFileSpy).toHaveBeenCalledTimes(3)
            expect(deleteFileSpy).toHaveBeenNthCalledWith(1, exampleCollectionId, exampleFileData.id, undefined)
            expect(deleteFileSpy).toHaveBeenNthCalledWith(2, exampleCollectionId, exampleChildFile1.id, undefined)
            expect(deleteFileSpy).toHaveBeenNthCalledWith(3, exampleCollectionId, exampleChildDirectory1.id, undefined)
            expect((resultAction.payload.error as FileDeleteFailed).deleted).toStrictEqual([
                exampleFileData.id,
                exampleChildDirectory1.id,
            ])
            expect((resultAction.payload.error as FileDeleteFailed).errors).toStrictEqual([
                {
                    collectionId: exampleCollectionId,
                    fileId: exampleChildFile1.id,
                    status: "error",
                    error: new Error("Some error"),
                },
            ])
        })
    })

    describe("encrypt and update version", () => {
        const params: NewVersionData = {
            collectionId: exampleCollectionId,
            fileId: exampleFileData.id,
            latestVersionDigest: exampleFileData.contentDigest,
            file: fileContentWithArrayBuffer,
            encryptionKey: exampleEncryptionKey,
        }

        it("should upload new version", async () => {
            const resultData: FileMetadata = {
                ...exampleFileData,
                contentDigest: "digest(3132322c3132332c3132342c313235)",
                versionId: "new-version",
            }

            const uploadSpy = jest.spyOn(FilesApi, "uploadVersion").mockResolvedValue(resultData)

            const trigger = encryptAndUpdateVersionAction.started(params)

            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                encryptAndUpdateVersionAction.done({ params, result: resultData })
            )

            expect(uploadSpy).toHaveBeenCalledTimes(1)
            expect(uploadSpy).toHaveBeenCalledWith({
                collectionId: exampleCollectionId,
                content: {
                    algorithm: "AES-CBC",
                    bytes: "encrypted(122,123,124,125, AES-CBC",
                    encryptionKeyVersion: exampleEncryptionKey.version,
                    iv: "someIv",
                },
                contentDigest: "digest(3132322c3132332c3132342c313235)",
                fileId: exampleFileData.id,
                mimeType: fileContentWithArrayBuffer.type,
            })
        })

        it("should not upload new version if collection ID of encryption key is not current collection", async () => {
            const updatedParams = {
                ...params,
                encryptionKey: { ...params.encryptionKey, collectionId: "other-collection" },
            }

            const trigger = encryptAndUpdateVersionAction.started(updatedParams)

            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                encryptAndUpdateVersionAction.failed({
                    params: updatedParams,
                    error: new Error(
                        `Provided encryption key of collection other-collection instead of ${exampleCollectionId}`
                    ),
                })
            )
        })

        it("should not upload new version if content digest has not changed", async () => {
            const updatedParams = {
                ...params,
                latestVersionDigest: "digest(3132322c3132332c3132342c313235)",
            }

            const trigger = encryptAndUpdateVersionAction.started(updatedParams)

            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                encryptAndUpdateVersionAction.failed({
                    params: updatedParams,
                    error: new FileContentNotChanged(exampleCollectionId, exampleFileData.id),
                })
            )
        })
    })

    describe("download and decrypt file", () => {
        const params: DownloadFileParams = {
            collectionId: exampleCollectionId,
            fileId: exampleFileData.id,
            encryptionKey: exampleEncryptionKey,
        }

        it("should download file", async () => {
            const downloadSpy = jest.spyOn(FilesApi, "readFile").mockResolvedValue({
                content: {
                    algorithm: "AES-CBC",
                    iv: "000102",
                    encryptionKeyVersion: exampleEncryptionKey.version,
                    bytes: "466f6f426172",
                },
                digest: "digest(3130302c3130312c39392c3131342c3132312c3131322c3131362c3130312c3130302c34302c36352c36392c38332c34352c36372c36362c36372c35382c34382c34382c34382c34392c34382c35302c35382c35322c35342c35342c3130322c35342c3130322c35322c35302c35342c34392c35352c35302c34352c34352c34352c36352c36392c38332c34352c36372c36362c36372c35382c39372c39372c39382c39382c39392c39392c35382c34392c39372c35302c39382c35312c39392c3431)",
                name: exampleFileData.name,
                mimeType: exampleFileData.mimeType,
            })

            const trigger = downloadAndDecryptFileAction.started(params)

            const expectedData = new TextEncoder().encode(
                `decrypted(AES-CBC:000102:466f6f426172---${exampleEncryptionKey.key})`
            )

            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                downloadAndDecryptFileAction.done({
                    params,
                    result: {
                        data: expectedData,
                        name: exampleFileData.name,
                        mimeType: exampleFileData.mimeType,
                    },
                })
            )

            expect(downloadSpy).toHaveBeenCalledTimes(1)
            expect(downloadSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id, undefined)
        })

        it("should download file with specified version", async () => {
            const updatedParams = { ...params, versionId: exampleVersionId }

            const downloadSpy = jest.spyOn(FilesApi, "readFile").mockResolvedValue({
                content: {
                    algorithm: "AES-CBC",
                    iv: "000102",
                    encryptionKeyVersion: exampleEncryptionKey.version,
                    bytes: "466f6f426172",
                },
                digest: "digest(3130302c3130312c39392c3131342c3132312c3131322c3131362c3130312c3130302c34302c36352c36392c38332c34352c36372c36362c36372c35382c34382c34382c34382c34392c34382c35302c35382c35322c35342c35342c3130322c35342c3130322c35322c35302c35342c34392c35352c35302c34352c34352c34352c36352c36392c38332c34352c36372c36362c36372c35382c39372c39372c39382c39382c39392c39392c35382c34392c39372c35302c39382c35312c39392c3431)",
                name: exampleFileData.name,
                mimeType: exampleFileData.mimeType,
            })

            const trigger = downloadAndDecryptFileAction.started(updatedParams)

            const expectedData = new TextEncoder().encode(
                `decrypted(AES-CBC:000102:466f6f426172---${exampleEncryptionKey.key})`
            )

            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                downloadAndDecryptFileAction.done({
                    params: updatedParams,
                    result: {
                        data: expectedData,
                        name: exampleFileData.name,
                        mimeType: exampleFileData.mimeType,
                    },
                })
            )

            expect(downloadSpy).toHaveBeenCalledTimes(1)
            expect(downloadSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id, exampleVersionId)
        })

        it("should not download file if encryption key collection ID does not match to current collection", async () => {
            const updatedParams = { ...params, collectionId: "other-collection" }

            const trigger = downloadAndDecryptFileAction.started(updatedParams)

            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                downloadAndDecryptFileAction.failed({
                    params: updatedParams,
                    error: new Error(
                        `Provided encryption key of collection ${exampleCollectionId} instead of other-collection`
                    ),
                })
            )
        })

        it("should not decrypt file if encryption key version does not match", async () => {
            const downloadSpy = jest.spyOn(FilesApi, "readFile").mockResolvedValue({
                content: {
                    algorithm: "AES-CBC",
                    iv: "000102",
                    encryptionKeyVersion: "other-key",
                    bytes: "466f6f426172",
                },
                digest: "digest(3130302c3130312c39392c3131342c3132312c3131322c3131362c3130312c3130302c34302c36352c36392c38332c34352c36372c36362c36372c35382c34382c34382c34382c34392c34382c35302c35382c35322c35342c35342c3130322c35342c3130322c35322c35302c35342c34392c35352c35302c34352c34352c34352c36352c36392c38332c34352c36372c36362c36372c35382c39372c39372c39382c39382c39392c39392c35382c34392c39372c35302c39382c35312c39392c3431)",
                name: exampleFileData.name,
                mimeType: exampleFileData.mimeType,
            })

            const trigger = downloadAndDecryptFileAction.started(params)

            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                downloadAndDecryptFileAction.failed({
                    params,
                    error: new Error(
                        `File was encrypted with collection key version other-key but key was provided with version ${exampleEncryptionKey.version}`
                    ),
                })
            )

            expect(downloadSpy).toHaveBeenCalledTimes(1)
            expect(downloadSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id, undefined)
        })

        it("should return error if content digest does not match", async () => {
            const downloadSpy = jest.spyOn(FilesApi, "readFile").mockResolvedValue({
                content: {
                    algorithm: "AES-CBC",
                    iv: "000102",
                    encryptionKeyVersion: exampleEncryptionKey.version,
                    bytes: "466f6f426172",
                },
                digest: "other-digest",
                name: exampleFileData.name,
                mimeType: exampleFileData.mimeType,
            })

            const trigger = downloadAndDecryptFileAction.started(params)

            await verifyAsyncEpic(
                trigger,
                filesEpics,
                defaultState,
                downloadAndDecryptFileAction.failed({
                    params,
                    error: new Error(
                        "Expected decrypted file digest to be other-digest but got digest(3130302c3130312c39392c3131342c3132312c3131322c3131362c3130312c3130302c34302c36352c36392c38332c34352c36372c36362c36372c35382c34382c34382c34382c34392c34382c35302c35382c35322c35342c35342c3130322c35342c3130322c35322c35302c35342c34392c35352c35302c34352c34352c34352c36352c36392c38332c34352c36372c36362c36372c35382c39372c39372c39382c39382c39392c39392c35382c34392c39372c35302c39382c35312c39392c3431)"
                    ),
                })
            )

            expect(downloadSpy).toHaveBeenCalledTimes(1)
            expect(downloadSpy).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id, undefined)
        })
    })
})
