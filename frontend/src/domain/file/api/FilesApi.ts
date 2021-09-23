import { OptionalValue } from "./../../../application/api/OptionalValue"
import { errorResponseReasonToError, errorResponseToError, validatedResponse } from "./../../../application/api/helpers"
import { client } from "../../../application/api/BaseClient"
import {
    DirectoryMetadata,
    DirectoryMetadataSchema,
    FileMetadata,
    FileMetadataSchema,
    FilesystemUnitMetadata,
    FilesystemUnitMetadataSchema,
} from "./../types/FilesystemUnitMetadata"
import { FileAlreadyExists, FileContentNotChanged, FileNotFound, NotADirectory, RecursivelyDelete } from "./errors"
import { NewDirectoryData } from "../store/Actions"
import { EncryptedFile, EncryptedFileSchema } from "../types/EncryptedFile"
import { FileVersion, FileVersionSchema } from "../types/FileVersion"

type EncryptedFileContent = {
    algorithm: string
    iv: string
    encryptionKeyVersion: string
    bytes: string
}

export type UploadData = {
    collectionId: string
    parent: string | null
    name: string
    mimeType?: string | null
    contentDigest: string
    content: EncryptedFileContent
}

export type EditMetadata = {
    name?: string | null
    parent?: OptionalValue<string> | null
}

export type VersionUpdateData = {
    collectionId: string
    fileId: string
    mimeType?: string
    content: EncryptedFileContent
    contentDigest: string
}

const api = {
    getMetadata(collectionId: string, fileId: string): Promise<FilesystemUnitMetadata> {
        return client
            .get(`collections/${collectionId}/files/${fileId}/metadata`)
            .then((resp) => validatedResponse(resp, FilesystemUnitMetadataSchema))
            .catch((err) => errorResponseToError(err, new FileNotFound(fileId, collectionId), 404, 403, 400))
    },
    getParents(collectionId: string, fileId: string): Promise<DirectoryMetadata[]> {
        return client
            .get(`collections/${collectionId}/files/${fileId}/parents`)
            .then((resp) => validatedResponse(resp, DirectoryMetadataSchema.array()))
            .catch((err) => errorResponseToError(err, new FileNotFound(fileId, collectionId), 404, 403, 400))
    },
    getChildren(collectionId: string, directoryId: string | null): Promise<FilesystemUnitMetadata[]> {
        const baseUrl = `collections/${collectionId}/files`
        const url = directoryId === null ? baseUrl + "/children" : baseUrl + `/${directoryId}/children`

        return client
            .get(url)
            .then((resp) => validatedResponse(resp, FilesystemUnitMetadataSchema.array()))
            .catch((err) =>
                directoryId !== null
                    ? errorResponseToError(err, new FileNotFound(directoryId, collectionId), 404, 403, 400)
                    : Promise.reject(err)
            )
            .catch((err) =>
                directoryId !== null
                    ? errorResponseReasonToError(err, new NotADirectory(directoryId), 412, "NotADirectory")
                    : Promise.reject(err)
            )
    },
    createDirectory(newDirectoryData: NewDirectoryData): Promise<DirectoryMetadata> {
        const { collectionId, ...payload } = newDirectoryData
        return client
            .post(`collections/${collectionId}/files`, { json: { ...payload, "@type": "NewDirectory" } })
            .then((resp) => validatedResponse(resp, DirectoryMetadataSchema))
            .catch((err) =>
                errorResponseToError(err, new FileAlreadyExists(collectionId, payload.parent, payload.name), 409)
            )
    },
    uploadFile(uploadData: UploadData): Promise<FileMetadata> {
        const { collectionId, ...payload } = uploadData
        return client
            .post(`collections/${collectionId}/files`, { json: { ...payload, "@type": "NewFile" } })
            .then((resp) => validatedResponse(resp, FileMetadataSchema))
            .catch((err) =>
                errorResponseToError(err, new FileAlreadyExists(collectionId, payload.parent, payload.name), 409)
            )
    },
    editMetadata(fileId: string, collectionId: string, newMetadata: EditMetadata): Promise<FilesystemUnitMetadata> {
        return client
            .patch(`collections/${collectionId}/files/${fileId}`, { json: newMetadata })
            .then((resp) => validatedResponse(resp, FilesystemUnitMetadataSchema))
            .catch((err) =>
                errorResponseToError(
                    err,
                    new FileAlreadyExists(collectionId, "unknown", newMetadata.name ?? "unknown"),
                    409
                )
            )
    },
    deleteFile(collectionId: string, fileId: string, deleteNonEmpty?: boolean): Promise<void> {
        const searchParams = deleteNonEmpty === undefined ? undefined : { deleteNonEmpty }

        return client
            .delete(`collections/${collectionId}/files/${fileId}`, { searchParams })
            .then(() => void 0)
            .catch((err) =>
                errorResponseReasonToError(err, new RecursivelyDelete(collectionId, fileId), 412, "RecursivelyDelete")
            )
    },
    uploadVersion(updateData: VersionUpdateData): Promise<FileMetadata> {
        const { collectionId, fileId, ...payload } = updateData
        return client
            .post(`collections/${collectionId}/files/${fileId}/versions`, { json: payload })
            .then((resp) => validatedResponse(resp, FileMetadataSchema))
            .catch((err) =>
                errorResponseReasonToError(
                    err,
                    new FileContentNotChanged(collectionId, fileId),
                    422,
                    "FileContentNotChanged"
                )
            )
    },
    readFile(collectionId: string, fileId: string, versionId?: string): Promise<EncryptedFile> {
        const searchParams = versionId === undefined ? undefined : { versionId }

        return client
            .get(`collections/${collectionId}/files/${fileId}/content`, { searchParams })
            .then((resp) => validatedResponse(resp, EncryptedFileSchema))
    },
    listVersions(collectionId: string, fileId: string): Promise<FileVersion[]> {
        return client
            .get(`collections/${collectionId}/files/${fileId}/versions`)
            .then((resp) => validatedResponse(resp, FileVersionSchema.array()))
    },
    deleteVersion(collectionId: string, fileId: string, versionId: string): Promise<void> {
        return client.delete(`collections/${collectionId}/files/${fileId}/versions/${versionId}`).then(() => void 0)
    },
}

export default api
