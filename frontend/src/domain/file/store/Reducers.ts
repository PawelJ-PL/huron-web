import { FileVersion } from "./../types/FileVersion"
import { reducerWithInitialState } from "typescript-fsa-reducers"
import { FileTree, isRootDirectoryTree, ObjectTree } from "./../types/ObjectTree"
import { FileMetadata, FilesystemUnitMetadata } from "./../types/FilesystemUnitMetadata"
import { combineReducers } from "redux"
import { createReducer } from "./../../../application/store/async/AsyncActionReducer"
import {
    createDirectoryAction,
    deleteFilesAction,
    deleteFilesRequestAction,
    deleteVersionAction,
    deleteVersionRequestAction,
    downloadAndDecryptFileAction,
    encryptAndUpdateVersionAction,
    encryptAndUploadFileAction,
    fetchObjectTreeAction,
    listFileVersionsAction,
    renameFileAction,
    renameRequestAction,
    requestVersionUpdateAction,
    resetCreateDirectoryStatusAction,
    resetCurrentObjectTreeAction,
    resetDeleteFilesResultAction,
    resetDeleteVersionResultAction,
    resetDownloadFileResultAction,
    resetEncryptAndUpdateVersionStatusAction,
    resetEncryptAndUploadFileStatusAction,
    resetListFileVersionsResultAction,
    resetRenameResultAction,
    selectFilesAction,
} from "./Actions"
import sortedIndex from "lodash/sortedIndex"
import { isFileTree } from "../types/ObjectTree"
import { AsyncOperationResult } from "../../../application/store/async/AsyncOperationResult"
import { FileDeleteFailed } from "../types/errors"
import { deleteFromSetBy } from "../../../application/utils/collections"
import omit from "lodash/omit"

const insertObject = (list: FilesystemUnitMetadata[], obj: FilesystemUnitMetadata) => {
    const sortedNames = list.map((o) =>
        o["@type"] === "DirectoryData" ? `d_${o.name.toLowerCase()}` : `f_${o.name.toLowerCase()}`
    )
    const newObjIndex = sortedIndex(
        sortedNames,
        obj["@type"] === "DirectoryData" ? `d_${obj.name.toLowerCase()}` : `f_${obj.name.toLowerCase()}`
    )
    return [...list.slice(0, newObjIndex), obj, ...list.slice(newObjIndex)]
}

const updateChildrenInCurrentTreeIfNeeded = (
    state: AsyncOperationResult<{ collectionId: string; objectId: string | null }, ObjectTree, Error>,
    action: { params: { collectionId: string; parent: string | null }; result: FilesystemUnitMetadata }
) => {
    if (
        state.status === "FINISHED" &&
        state.params.collectionId === action.params.collectionId &&
        state.params.objectId === action.params.parent &&
        !isFileTree(state.data)
    ) {
        const updatedChildren = insertObject(state.data.children, action.result)
        return { ...state, data: { ...state.data, children: updatedChildren } }
    } else {
        return state
    }
}

const fetchCurrentObjectTreeReducer = createReducer(fetchObjectTreeAction, resetCurrentObjectTreeAction)
    .cases([createDirectoryAction.done, encryptAndUploadFileAction.done], (state, action) => {
        return updateChildrenInCurrentTreeIfNeeded(state, action)
    })
    .case(renameFileAction.done, (state, action) => {
        if (
            state.status === "FINISHED" &&
            state.params.collectionId === action.params.collectionId &&
            !isFileTree(state.data) &&
            state.data.children.map((child) => child.id).includes(action.params.fileId)
        ) {
            const removed = state.data.children.filter((child) => child.id !== action.params.fileId)
            const updated = insertObject(removed, action.result)
            return { ...state, data: { ...state.data, children: updated } }
        } else if (
            state.status === "FINISHED" &&
            state.params.collectionId === action.params.collectionId &&
            !isRootDirectoryTree(state.data) &&
            state.data.metadata.id === action.params.fileId
        ) {
            return { ...state, data: { ...state.data, metadata: { ...state.data.metadata, name: action.result.name } } }
        }

        return state
    })
    .case(deleteFilesAction.done, (state, action) => {
        if (
            state.status === "FINISHED" &&
            state.params.collectionId === action.params.collectionId &&
            !isFileTree(state.data) &&
            state.data.children.map((child) => child.id).some((childId) => action.params.fileIds.includes(childId))
        ) {
            const updated = state.data.children.filter((child) => !action.params.fileIds.includes(child.id))
            return { ...state, data: { ...state.data, children: updated } }
        }
        return state
    })
    .case(deleteFilesAction.failed, (state, action) => {
        if (
            state.status === "FINISHED" &&
            state.params.collectionId === action.params.collectionId &&
            !isFileTree(state.data) &&
            action.error instanceof FileDeleteFailed
        ) {
            const deletedFiles = action.error.deleted
            const updated = state.data.children.filter((child) => !deletedFiles.includes(child.id))
            return { ...state, data: { ...state.data, children: updated } }
        }
        return state
    })
    .case(encryptAndUpdateVersionAction.done, (state, action) => {
        if (
            state.status === "FINISHED" &&
            state.params.collectionId === action.params.collectionId &&
            !isFileTree(state.data) &&
            state.data.children.some((child) => child.id === action.params.fileId)
        ) {
            const removed = state.data.children.filter((child) => child.id !== action.params.fileId)
            const updated = insertObject(removed, action.result)
            return { ...state, data: { ...state.data, children: updated } }
        } else if (
            state.status === "FINISHED" &&
            state.params.collectionId === action.params.collectionId &&
            state.params.objectId === action.params.fileId &&
            isFileTree(state.data)
        ) {
            const updatedResult: FileTree = { ...state.data, metadata: action.result }
            return { ...state, data: updatedResult }
        }
        return state
    })
    .build()

const createDirectoryReducer = createReducer(createDirectoryAction, resetCreateDirectoryStatusAction).build()

const fileUploadReducer = createReducer(encryptAndUploadFileAction, resetEncryptAndUploadFileStatusAction, {
    params: (params) => ({ ...params, encryptionKey: { ...params.encryptionKey, key: "" } }),
}).build()

const renameReducer = createReducer(renameFileAction, resetRenameResultAction).build()

const renameRequestReducer = reducerWithInitialState<FilesystemUnitMetadata | null>(null)
    .case(renameRequestAction, (_, action) => action)
    .build()

const deleteFilesRequestReducer = reducerWithInitialState<FilesystemUnitMetadata[] | null>(null)
    .case(deleteFilesRequestAction, (_, action) => action)
    .build()

const deleteFilesReducer = createReducer(deleteFilesAction, resetDeleteFilesResultAction).build()

const selectedFilesReducer = reducerWithInitialState<Set<FilesystemUnitMetadata>>(new Set())
    .case(selectFilesAction, (_, action) => action)
    .case(deleteFilesAction.done, (state, action) =>
        deleteFromSetBy(state, (f) => action.params.fileIds.includes(f.id))
    )
    .case(deleteFilesAction.failed, (state, action) => {
        if (action.error instanceof FileDeleteFailed) {
            const deleted = action.error.deleted
            return deleteFromSetBy(state, (f) => deleted.includes(f.id))
        } else {
            return state
        }
    })
    .build()

const updateVersionReducer = createReducer(encryptAndUpdateVersionAction, resetEncryptAndUpdateVersionStatusAction, {
    params: (params) => ({ ...params, encryptionKey: { ...params.encryptionKey, key: "" } }),
}).build()

const requestVersionUpdateReducer = reducerWithInitialState<FileMetadata | null>(null)
    .case(requestVersionUpdateAction, (_, action) => action)
    .build()

const downloadFileReducer = createReducer(downloadAndDecryptFileAction, resetDownloadFileResultAction, {
    params: (params) => ({ ...params, encryptionKey: { ...params.encryptionKey, key: "" } }),
}).build()

const listFileVersionsReducer = createReducer(listFileVersionsAction, resetListFileVersionsResultAction)
    .case(encryptAndUpdateVersionAction.done, (state, action) => {
        if (
            state.status === "FINISHED" &&
            state.params.collectionId === action.params.collectionId &&
            state.params.fileId === action.params.fileId
        ) {
            const newVersion = omit(action.result, ["@type", "name", "parent"])
            const updated = [newVersion, ...state.data]
            return { ...state, data: updated }
        } else {
            return state
        }
    })
    .case(deleteVersionAction.done, (state, action) => {
        if (
            state.status === "FINISHED" &&
            state.params.collectionId === action.params.collectionId &&
            state.params.fileId === action.params.fileId
        ) {
            const updated = state.data.filter((version) => version.versionId !== action.params.versionId)
            return { ...state, data: updated }
        } else {
            return state
        }
    })
    .build()

const deleteVersionRequestReducer = reducerWithInitialState<FileVersion | null>(null)
    .case(deleteVersionRequestAction, (_, action) => action)
    .build()

const deleteVersionReducer = createReducer(deleteVersionAction, resetDeleteVersionResultAction).build()

export const filesReducer = combineReducers({
    currentObjectTree: fetchCurrentObjectTreeReducer,
    createDirectoryResult: createDirectoryReducer,
    fileUploadResult: fileUploadReducer,
    renameRequest: renameRequestReducer,
    renameResult: renameReducer,
    deleteFilesRequest: deleteFilesRequestReducer,
    deleteFilesResult: deleteFilesReducer,
    selectedFiles: selectedFilesReducer,
    requestedVersionUpdate: requestVersionUpdateReducer,
    versionUpdateResult: updateVersionReducer,
    downloadFileResult: downloadFileReducer,
    fileVersionsResult: listFileVersionsReducer,
    versionToDelete: deleteVersionRequestReducer,
    deleteVersionResult: deleteVersionReducer,
})
