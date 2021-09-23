import { DecryptedFile } from "./../types/DecryptedFile"
import { EncryptionKey } from "./../../collection/types/EncryptionKey"
import { DirectoryMetadata, FileMetadata, FilesystemUnitMetadata } from "./../types/FilesystemUnitMetadata"
import { combineEpics } from "redux-observable"
import { ObjectTree } from "./../types/ObjectTree"
import { createEpic } from "./../../../application/store/async/AsyncActionEpic"
import {
    createDirectoryAction,
    deleteFilesAction,
    DeleteFilesParams,
    deleteVersionAction,
    DeleteVersionParams,
    downloadAndDecryptFileAction,
    DownloadFileParams,
    encryptAndUpdateVersionAction,
    encryptAndUploadFileAction,
    fetchObjectTreeAction,
    listFileVersionsAction,
    ListVersionParams,
    NewDirectoryData,
    NewFileData,
    NewVersionData,
    renameFileAction,
    RenameParams,
} from "./Actions"
import FilesApi, { UploadData, VersionUpdateData } from "../api/FilesApi"
import { AppState } from "../../../application/store"
import { Action } from "redux"
import * as forge from "node-forge"
import CryptoApi from "../../../application/cryptography/api/CryptoApi"
import { MAX_FILE_SIZE } from "../components/metadata_view/directory/UploadFileModal"
import { EncryptedFileTooLarge, FileContentNotChanged } from "../api/errors"
import { deferredPromise } from "../../../application/utils/promiseUtils"
import { FileDeleteFailed } from "../types/errors"
import { FileVersion } from "../types/FileVersion"

const fetchTreeEpic = createEpic<{ collectionId: string; objectId: string | null }, ObjectTree, Error>(
    fetchObjectTreeAction,
    async (params) => {
        if (params.objectId === null) {
            const children = await FilesApi.getChildren(params.collectionId, null)
            return { children }
        } else {
            const metadata = await FilesApi.getMetadata(params.collectionId, params.objectId)
            const parents =
                metadata.parent !== undefined && metadata.parent !== null
                    ? await FilesApi.getParents(params.collectionId, params.objectId)
                    : await Promise.resolve([])
            if (metadata["@type"] === "FileData") {
                return { metadata, parents: parents }
            } else {
                const children = await FilesApi.getChildren(params.collectionId, params.objectId)
                return { metadata, parents: parents, children }
            }
        }
    }
)

const createDirectoryEpic = createEpic<NewDirectoryData, DirectoryMetadata, Error>(createDirectoryAction, (params) =>
    FilesApi.createDirectory(params)
)

const encryptFile = async (file: File, encryptionKey: EncryptionKey) => {
    const fileContent = await file.arrayBuffer()
    const contentAsHex = await arrayBufferToHex(fileContent)
    const contentDigest = await CryptoApi.digest(contentAsHex)
    const encrypted = await CryptoApi.encryptBinary(fileContent, encryptionKey.key)
    const [algorithm, iv, bytes] = encrypted.split(":")
    if (bytes.length > MAX_FILE_SIZE * 2) {
        return Promise.reject(new EncryptedFileTooLarge(bytes.length / 2, MAX_FILE_SIZE)) as Promise<never>
    }
    const content = { algorithm, iv, encryptionKeyVersion: encryptionKey.version, bytes }
    return { content, contentDigest }
}

const encryptAndUploadFileEpic = createEpic<NewFileData, FileMetadata, Error>(
    encryptAndUploadFileAction,
    async ({ collectionId, encryptionKey, file, parent }) => {
        if (collectionId !== encryptionKey.collectionId) {
            return Promise.reject(
                new Error(
                    `Provided encryption key of collection ${encryptionKey.collectionId} instead of ${collectionId}`
                )
            ) as Promise<never>
        }
        const { content, contentDigest } = await encryptFile(file, encryptionKey)
        const mimeType = file.type || undefined
        const data: UploadData = {
            collectionId,
            parent,
            name: file.name,
            mimeType,
            contentDigest,
            content,
        }
        return FilesApi.uploadFile(data)
    }
)

const arrayBufferToHex = async (input: ArrayBuffer) => {
    const chunkSize = 1024 * 100

    const array = new Uint8Array(input)

    let index = 0
    let result = ""

    do {
        const buffer = await deferredPromise(forge.util.createBuffer, [input.slice(index, index + chunkSize)])
        const hex = await new Promise((resolve, reject) =>
            setTimeout(() => {
                try {
                    const result = buffer.toHex()
                    resolve(result)
                } catch (err) {
                    reject(err)
                }
            }, 0)
        )
        result = result + hex

        index += chunkSize
    } while (index < array.length)

    return result
}

const renameFileEpic = createEpic<RenameParams, FilesystemUnitMetadata, Error>(
    renameFileAction,
    ({ collectionId, fileId, newName }) => FilesApi.editMetadata(fileId, collectionId, { name: newName })
)

type DeleteFileErrorResult = { collectionId: string; fileId: string; status: "error"; error: Error }

type DeleteFileSuccessResult = { collectionId: string; fileId: string; status: "success" }

function deleteSingleFile(
    collectionId: string,
    fileId: string,
    deleteNonEmpty: boolean | undefined
): Promise<DeleteFileSuccessResult | DeleteFileErrorResult> {
    return FilesApi.deleteFile(collectionId, fileId, deleteNonEmpty)
        .then(() => {
            const result: DeleteFileSuccessResult = { fileId, collectionId, status: "success" }
            return result
        })
        .catch((error) => {
            const result: DeleteFileErrorResult = { fileId, collectionId, status: "error", error }
            return result
        })
}

const deleteFilesEpic = createEpic<DeleteFilesParams, void, Error>(
    deleteFilesAction,
    ({ fileIds, collectionId, deleteNonEmpty }) =>
        Promise.all(fileIds.map((fileId) => deleteSingleFile(collectionId, fileId, deleteNonEmpty))).then((results) => {
            const errors: DeleteFileErrorResult[] = results.filter(
                (r): r is DeleteFileErrorResult => r.status === "error"
            )
            const deleted = results.filter((r) => r.status === "success").map((r) => r.fileId)
            if (errors.length > 0) {
                return Promise.reject(new FileDeleteFailed(deleted, errors))
            }
        })
)

const encryptAndUpdateVersionEpic = createEpic<NewVersionData, FileMetadata, Error>(
    encryptAndUpdateVersionAction,
    async ({ collectionId, fileId, file, encryptionKey, latestVersionDigest }) => {
        if (collectionId !== encryptionKey.collectionId) {
            return Promise.reject(
                new Error(
                    `Provided encryption key of collection ${encryptionKey.collectionId} instead of ${collectionId}`
                )
            ) as Promise<never>
        }
        const { content, contentDigest } = await encryptFile(file, encryptionKey)
        if (contentDigest === latestVersionDigest) {
            return Promise.reject(new FileContentNotChanged(collectionId, fileId)) as Promise<never>
        }
        const mimeType = file.type || undefined
        const data: VersionUpdateData = {
            fileId,
            collectionId,
            mimeType,
            contentDigest,
            content,
        }
        return FilesApi.uploadVersion(data)
    }
)

const downloadAndDecryptFileEpic = createEpic<DownloadFileParams, DecryptedFile, Error>(
    downloadAndDecryptFileAction,
    async ({ fileId, collectionId, versionId, encryptionKey }) => {
        if (collectionId !== encryptionKey.collectionId) {
            return Promise.reject(
                new Error(
                    `Provided encryption key of collection ${encryptionKey.collectionId} instead of ${collectionId}`
                )
            ) as Promise<never>
        }
        const encryptedFile = await FilesApi.readFile(collectionId, fileId, versionId)
        if (encryptedFile.content.encryptionKeyVersion !== encryptionKey.version) {
            return Promise.reject(
                new Error(
                    `File was encrypted with collection key version ${encryptedFile.content.encryptionKeyVersion} but key was provided with version ${encryptionKey.version}`
                )
            ) as Promise<never>
        }
        const encryptedContent = `${encryptedFile.content.algorithm}:${encryptedFile.content.iv}:${encryptedFile.content.bytes}`
        const decrypted = await CryptoApi.decryptBinary(encryptedContent, encryptionKey.key)
        const contentAsHex = await arrayBufferToHex(decrypted)
        const contentDigest = await CryptoApi.digest(contentAsHex)
        if (contentDigest !== encryptedFile.digest) {
            return Promise.reject(
                new Error(`Expected decrypted file digest to be ${encryptedFile.digest} but got ${contentDigest}`)
            ) as Promise<never>
        }
        return {
            data: decrypted,
            name: encryptedFile.name,
            mimeType: encryptedFile.mimeType,
        }
    }
)

const listFileVersionsEpic = createEpic<ListVersionParams, FileVersion[], Error>(
    listFileVersionsAction,
    ({ collectionId, fileId }) => FilesApi.listVersions(collectionId, fileId)
)

const deleteVersionEpic = createEpic<DeleteVersionParams, void, Error>(
    deleteVersionAction,
    ({ collectionId, fileId, versionId }) => FilesApi.deleteVersion(collectionId, fileId, versionId)
)

export const filesEpics = combineEpics<Action, Action, AppState>(
    fetchTreeEpic,
    createDirectoryEpic,
    encryptAndUploadFileEpic,
    renameFileEpic,
    deleteFilesEpic,
    encryptAndUpdateVersionEpic,
    downloadAndDecryptFileEpic,
    listFileVersionsEpic,
    deleteVersionEpic
)
