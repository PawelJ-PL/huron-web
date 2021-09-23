import { DecryptedFile } from "./../types/DecryptedFile"
import { EncryptionKey } from "./../../collection/types/EncryptionKey"
import { DirectoryMetadata, FileMetadata, FilesystemUnitMetadata } from "./../types/FilesystemUnitMetadata"
import { ObjectTree } from "./../types/ObjectTree"
import { actionCreatorFactory } from "typescript-fsa"
import { FileVersion } from "../types/FileVersion"

const actionCreator = actionCreatorFactory("FILES")

export type NewDirectoryData = {
    parent: string | null
    name: string
    collectionId: string
}

export type NewFileData = {
    collectionId: string
    parent: string | null
    file: File
    encryptionKey: EncryptionKey
}

export type RenameParams = {
    collectionId: string
    fileId: string
    newName: string
}

export type DeleteFilesParams = {
    collectionId: string
    fileIds: string[]
    deleteNonEmpty?: boolean
}

export const fetchObjectTreeAction = actionCreator.async<
    { collectionId: string; objectId: string | null },
    ObjectTree,
    Error
>("FETCH_OBJECT_TREE")

export type NewVersionData = {
    collectionId: string
    fileId: string
    latestVersionDigest: string
    file: File
    encryptionKey: EncryptionKey
}

export type DownloadFileParams = {
    collectionId: string
    fileId: string
    versionId?: string
    encryptionKey: EncryptionKey
}

export type ListVersionParams = {
    collectionId: string
    fileId: string
}

export type DeleteVersionParams = {
    collectionId: string
    fileId: string
    versionId: string
}

export const resetCurrentObjectTreeAction = actionCreator("RESET_CURRENT_OBJECT_TREE")

export const createDirectoryAction = actionCreator.async<NewDirectoryData, DirectoryMetadata, Error>("CREATE_DIRECTORY")
export const resetCreateDirectoryStatusAction = actionCreator("RESET_CREATE_DIRECTORY_STATUS")

export const encryptAndUploadFileAction = actionCreator.async<NewFileData, FileMetadata, Error>(
    "ENCRYPT_AND_UPLOAD_FILE"
)
export const resetEncryptAndUploadFileStatusAction = actionCreator("RESET_ENCRYPT_AND_UPLOAD_FILE_STATUS")

export const renameRequestAction = actionCreator<FilesystemUnitMetadata | null>("RENAME_REQUEST")
export const renameFileAction = actionCreator.async<RenameParams, FilesystemUnitMetadata, Error>("RENAME_FILE")
export const resetRenameResultAction = actionCreator("RESET_RENAME_RESULT")

export const deleteFilesAction = actionCreator.async<DeleteFilesParams, void, Error>("DELETE_FILES")
export const resetDeleteFilesResultAction = actionCreator("RESET_DELETE_FILES_RESULT")
export const deleteFilesRequestAction = actionCreator<FilesystemUnitMetadata[] | null>("DELETE_FILES_REQUEST")

export const selectFilesAction = actionCreator<Set<FilesystemUnitMetadata>>("SELECT_FILES")

export const encryptAndUpdateVersionAction = actionCreator.async<NewVersionData, FileMetadata, Error>(
    "ENCRYPT_AND_UPDATE_ACTION"
)
export const resetEncryptAndUpdateVersionStatusAction = actionCreator("RESET_ENCRYPT_AND_UPDATE_VERSION_STATUS")
export const requestVersionUpdateAction = actionCreator<FileMetadata | null>("REQUEST_VERSION_UPDATE")

export const downloadAndDecryptFileAction = actionCreator.async<DownloadFileParams, DecryptedFile, Error>(
    "DOWNLOAD_AND_DECRYPT_FILE"
)
export const resetDownloadFileResultAction = actionCreator("RESET_DOWNLOAD_FILE_RESULT")

export const listFileVersionsAction = actionCreator.async<ListVersionParams, FileVersion[], Error>("LIST_FILE_VERSIONS")
export const resetListFileVersionsResultAction = actionCreator("RESET_LIST_FILE_VERSIONS_RESULT")

export const deleteVersionAction = actionCreator.async<DeleteVersionParams, void, Error>("DELETE_VERSION")
export const resetDeleteVersionResultAction = actionCreator("RESET_DELETE_VERSION_RESULT_ACTION")
export const deleteVersionRequestAction = actionCreator<FileVersion | null>("DELETE_VERSION_REQUEST")
