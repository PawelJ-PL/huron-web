import { FileDeleteFailed } from "../types/errors"
import { FileTree } from "../types/ObjectTree"
import { exampleCollectionId, exampleEncryptionKey } from "./../../../testutils/constants/collection"
import {
    exampleChildDirectory1,
    exampleChildDirectory2,
    exampleChildFile1,
    exampleChildFile2,
    exampleCurrentDirectory,
    exampleDirectoryData,
    exampleDirectoryTree,
    exampleFileContent,
    exampleFileData,
    exampleFileTree,
    exampleVersion,
} from "./../../../testutils/constants/files"
import {
    createDirectoryAction,
    deleteFilesAction,
    DeleteFilesParams,
    deleteVersionAction,
    DeleteVersionParams,
    encryptAndUpdateVersionAction,
    encryptAndUploadFileAction,
    ListVersionParams,
    NewDirectoryData,
    NewFileData,
    NewVersionData,
    renameFileAction,
    RenameParams,
    selectFilesAction,
} from "./Actions"
import { filesReducer } from "./Reducers"

type State = ReturnType<typeof filesReducer>

const defaultState = {} as State

const currentTreeParams = { collectionId: exampleCurrentDirectory.collectionId, objectId: exampleCurrentDirectory.id }

const newDirectoryParams: NewDirectoryData = {
    parent: exampleDirectoryData.parent ?? null,
    name: exampleDirectoryData.name,
    collectionId: exampleDirectoryData.collectionId,
}

const newFileParams: NewFileData = {
    collectionId: exampleFileData.collectionId,
    parent: exampleFileData.parent ?? null,
    encryptionKey: exampleEncryptionKey,
    file: exampleFileContent,
}

const renameParams: RenameParams = {
    collectionId: exampleChildFile2.collectionId,
    fileId: exampleChildFile2.id,
    newName: "aaa-file",
}

const deleteParams: DeleteFilesParams = {
    collectionId: exampleCollectionId,
    fileIds: [exampleChildDirectory2.id, exampleChildFile1.id],
    deleteNonEmpty: true,
}

const newVersionData: NewVersionData = {
    collectionId: exampleCollectionId,
    fileId: exampleChildFile1.id,
    latestVersionDigest: exampleChildFile1.contentDigest,
    file: exampleFileContent,
    encryptionKey: exampleEncryptionKey,
}

const listVersionsParams: ListVersionParams = {
    collectionId: exampleCollectionId,
    fileId: exampleFileData.id,
}

const deleteVersionParams: DeleteVersionParams = {
    collectionId: exampleCollectionId,
    fileId: exampleFileData.id,
    versionId: exampleFileData.versionId,
}

describe("Files reducers", () => {
    describe("Fetch objects tree", () => {
        it("should add newly created directory", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = createDirectoryAction.done({ params: newDirectoryParams, result: exampleDirectoryData })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual({
                status: "FINISHED",
                params: currentTreeParams,
                data: {
                    metadata: exampleCurrentDirectory,
                    parents: [],
                    children: [
                        exampleChildDirectory1,
                        exampleDirectoryData,
                        exampleChildDirectory2,
                        exampleChildFile1,
                        exampleChildFile2,
                    ],
                },
            })
        })

        it("should not add newly created directory if current state is not FINISHED", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "NOT_STARTED" },
            }

            const action = createDirectoryAction.done({ params: newDirectoryParams, result: exampleDirectoryData })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should not add newly created directory if collection id does not match", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = createDirectoryAction.done({
                params: { ...newDirectoryParams, collectionId: "other-collection" },
                result: { ...exampleDirectoryData },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should add newly uploaded file", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = encryptAndUploadFileAction.done({ params: newFileParams, result: exampleFileData })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual({
                status: "FINISHED",
                params: currentTreeParams,
                data: {
                    metadata: exampleCurrentDirectory,
                    parents: [],
                    children: [
                        exampleChildDirectory1,
                        exampleChildDirectory2,
                        exampleChildFile1,
                        exampleFileData,
                        exampleChildFile2,
                    ],
                },
            })
        })

        it("should update renamed child file", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = renameFileAction.done({
                params: renameParams,
                result: { ...exampleChildFile2, name: renameParams.newName },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual({
                status: "FINISHED",
                params: currentTreeParams,
                data: {
                    metadata: exampleCurrentDirectory,
                    parents: [],
                    children: [
                        exampleChildDirectory1,
                        exampleChildDirectory2,
                        { ...exampleChildFile2, name: renameParams.newName },
                        exampleChildFile1,
                    ],
                },
            })
        })

        it("should not update renamed child file if state is not FINISHED", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "NOT_STARTED" },
            }

            const action = renameFileAction.done({
                params: renameParams,
                result: { ...exampleChildFile2, name: renameParams.newName },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should not update renamed child file if collection id does not match", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = renameFileAction.done({
                params: { ...renameParams, collectionId: "other-collection-id" },
                result: { ...exampleChildFile2, name: renameParams.newName },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should not update renamed child file if directory not in children", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = renameFileAction.done({
                params: { ...renameParams, fileId: "other-file" },
                result: { ...exampleChildFile2, name: renameParams.newName },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should update renamed current directory", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = renameFileAction.done({
                params: { ...renameParams, fileId: exampleCurrentDirectory.id },
                result: { ...exampleCurrentDirectory, name: renameParams.newName },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual({
                status: "FINISHED",
                params: currentTreeParams,
                data: {
                    metadata: { ...exampleCurrentDirectory, name: renameParams.newName },
                    parents: [],
                    children: exampleDirectoryTree.children,
                },
            })
        })

        it("should update renamed current file", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: {
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, objectId: exampleFileData.id },
                    data: exampleFileTree,
                },
            }

            const action = renameFileAction.done({
                params: { ...renameParams, fileId: exampleFileData.id },
                result: { ...exampleFileData, name: renameParams.newName },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual({
                status: "FINISHED",
                params: { collectionId: exampleCollectionId, objectId: exampleFileData.id },
                data: {
                    metadata: { ...exampleFileData, name: renameParams.newName },
                    parents: [],
                },
            })
        })

        it("should not update renamed current directory if state status is not FINISHED", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "NOT_STARTED" },
            }

            const action = renameFileAction.done({
                params: { ...renameParams, fileId: exampleCurrentDirectory.id },
                result: { ...exampleCurrentDirectory, name: renameParams.newName },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should not update renamed current directory if collection id does not match", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = renameFileAction.done({
                params: { ...renameParams, fileId: exampleCurrentDirectory.id, collectionId: "other-collection" },
                result: { ...exampleCurrentDirectory, name: renameParams.newName },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should not update renamed current directory if file ID does not match", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = renameFileAction.done({
                params: { ...renameParams, fileId: "other-file-id" },
                result: { ...exampleCurrentDirectory, name: renameParams.newName },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should remove deleted files", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = deleteFilesAction.done({ params: deleteParams, result: undefined })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual({
                status: "FINISHED",
                params: currentTreeParams,
                data: {
                    metadata: exampleCurrentDirectory,
                    parents: [],
                    children: [exampleChildDirectory1, exampleChildFile2],
                },
            })
        })

        it("should not remove deleted files if state status is not FINISHED", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "NOT_STARTED" },
            }

            const action = deleteFilesAction.done({ params: deleteParams, result: undefined })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should not remove deleted files if collection ID does not match", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = deleteFilesAction.done({
                params: { ...deleteParams, collectionId: "other-collection" },
                result: undefined,
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should not remove deleted files if not present in current directory", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = deleteFilesAction.done({
                params: { ...deleteParams, fileIds: ["foo", "bar"] },
                result: undefined,
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should remove deleted files on error", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = deleteFilesAction.failed({
                params: { ...deleteParams, fileIds: [...deleteParams.fileIds, exampleChildFile2.id] },
                error: new FileDeleteFailed(
                    [exampleChildDirectory2.id, exampleChildFile1.id],
                    [{ fileId: exampleChildFile2.id, error: new Error("Some error") }]
                ),
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual({
                status: "FINISHED",
                params: currentTreeParams,
                data: {
                    metadata: exampleCurrentDirectory,
                    parents: [],
                    children: [exampleChildDirectory1, exampleChildFile2],
                },
            })
        })

        it("should update children with updated version", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = encryptAndUpdateVersionAction.done({ params: newVersionData, result: exampleFileData })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual({
                status: "FINISHED",
                params: currentTreeParams,
                data: {
                    metadata: exampleCurrentDirectory,
                    parents: [],
                    children: [exampleChildDirectory1, exampleChildDirectory2, exampleFileData, exampleChildFile2],
                },
            })
        })

        it("should not update children with updated version if state status is not FINISHED", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "NOT_STARTED" },
            }

            const action = encryptAndUpdateVersionAction.done({ params: newVersionData, result: exampleFileData })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should not update children with updated version if collection ID does not match", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = encryptAndUpdateVersionAction.done({
                params: { ...newVersionData, collectionId: "other-collection" },
                result: exampleFileData,
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should not update children with updated version if not present in children", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "FINISHED", params: currentTreeParams, data: exampleDirectoryTree },
            }

            const action = encryptAndUpdateVersionAction.done({
                params: { ...newVersionData, fileId: "other-file" },
                result: exampleFileData,
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should update file with new version", () => {
            const currentTree: FileTree = {
                parents: [],
                metadata: exampleFileData,
            }

            const state: State = {
                ...defaultState,
                currentObjectTree: {
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, objectId: exampleFileData.id },
                    data: currentTree,
                },
            }

            const action = encryptAndUpdateVersionAction.done({
                params: { ...newVersionData, fileId: exampleFileData.id },
                result: { ...exampleFileData, versionAuthor: "other-user" },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual({
                status: "FINISHED",
                params: { collectionId: exampleCollectionId, objectId: exampleFileData.id },
                data: {
                    metadata: { ...exampleFileData, versionAuthor: "other-user" },
                    parents: [],
                },
            })
        })

        it("should not update file with new version if current state status is not FINISHED", () => {
            const state: State = {
                ...defaultState,
                currentObjectTree: { status: "NOT_STARTED" },
            }

            const action = encryptAndUpdateVersionAction.done({
                params: { ...newVersionData, fileId: exampleFileData.id },
                result: { ...exampleFileData, versionAuthor: "other-user" },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should not update file with new version if collction ID does not match", () => {
            const currentTree: FileTree = {
                parents: [],
                metadata: exampleFileData,
            }

            const state: State = {
                ...defaultState,
                currentObjectTree: {
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, objectId: exampleFileData.id },
                    data: currentTree,
                },
            }

            const action = encryptAndUpdateVersionAction.done({
                params: { ...newVersionData, fileId: exampleFileData.id, collectionId: "other-collection" },
                result: { ...exampleFileData, versionAuthor: "other-user" },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })

        it("should update file with new version if file ID does not match", () => {
            const currentTree: FileTree = {
                parents: [],
                metadata: exampleFileData,
            }

            const state: State = {
                ...defaultState,
                currentObjectTree: {
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, objectId: exampleFileData.id },
                    data: currentTree,
                },
            }

            const action = encryptAndUpdateVersionAction.done({
                params: { ...newVersionData, fileId: "other-file" },
                result: { ...exampleFileData, versionAuthor: "other-user" },
            })

            const result = filesReducer(state, action)

            expect(result.currentObjectTree).toStrictEqual(state.currentObjectTree)
        })
    })

    describe("select files", () => {
        it("should be updated on action", () => {
            const action = selectFilesAction(new Set([exampleFileData]))

            const result = filesReducer(defaultState, action)

            expect(result.selectedFiles).toStrictEqual(new Set([exampleFileData]))
        })

        it("should remove items on delete success", () => {
            const action = deleteFilesAction.done({
                params: { collectionId: exampleCollectionId, fileIds: [exampleChildFile1.id, exampleChildFile2.id] },
                result: undefined,
            })

            const state: State = {
                ...defaultState,
                selectedFiles: new Set([exampleChildFile1, exampleFileData, exampleChildFile2]),
            }

            const result = filesReducer(state, action)

            expect(result.selectedFiles).toStrictEqual(new Set([exampleFileData]))
        })

        it("should remove items on delete error", () => {
            const action = deleteFilesAction.failed({
                params: { collectionId: exampleCollectionId, fileIds: [exampleChildFile1.id, exampleChildFile2.id] },
                error: new FileDeleteFailed(
                    [exampleChildFile2.id],
                    [{ fileId: exampleChildFile1.id, error: new Error("Some error") }]
                ),
            })

            const state: State = {
                ...defaultState,
                selectedFiles: new Set([exampleChildFile1, exampleFileData, exampleChildFile2]),
            }

            const result = filesReducer(state, action)

            expect(result.selectedFiles).toStrictEqual(new Set([exampleChildFile1, exampleFileData]))
        })
    })

    describe("list file versions", () => {
        it("should update list on new version upload", () => {
            const state: State = {
                ...defaultState,
                fileVersionsResult: { status: "FINISHED", params: listVersionsParams, data: [exampleVersion] },
            }

            const updatedVersion = {
                ...exampleVersion,
                versionId: "5fc516fd-aaeb-4256-8501-c1987455b2d0",
                contentDigest: "806fb3130a8b191071bd63c707af33166e13186d78d5b9873f42fc5404968258",
            }

            const action = encryptAndUpdateVersionAction.done({
                params: { ...newVersionData, fileId: exampleFileData.id },
                result: {
                    ...exampleFileData,
                    versionId: "5fc516fd-aaeb-4256-8501-c1987455b2d0",
                    contentDigest: "806fb3130a8b191071bd63c707af33166e13186d78d5b9873f42fc5404968258",
                },
            })

            const result = filesReducer(state, action)

            expect(result.fileVersionsResult).toStrictEqual({
                status: "FINISHED",
                params: {
                    collectionId: "1bffed24-f1a0-4108-839d-5741846a8606",
                    fileId: "99de9649-ea1c-4002-95e0-18a66cdda224",
                },
                data: [updatedVersion, exampleVersion],
            })
        })

        it("should not update list on new version upload if state status is not FINISHED", () => {
            const state: State = {
                ...defaultState,
                fileVersionsResult: { status: "NOT_STARTED" },
            }

            const action = encryptAndUpdateVersionAction.done({
                params: { ...newVersionData, fileId: exampleFileData.id },
                result: {
                    ...exampleFileData,
                    versionId: "5fc516fd-aaeb-4256-8501-c1987455b2d0",
                    contentDigest: "806fb3130a8b191071bd63c707af33166e13186d78d5b9873f42fc5404968258",
                },
            })

            const result = filesReducer(state, action)

            expect(result.fileVersionsResult).toStrictEqual(state.fileVersionsResult)
        })

        it("should not update list on new version upload if collection ID does not match", () => {
            const state: State = {
                ...defaultState,
                fileVersionsResult: { status: "FINISHED", params: listVersionsParams, data: [exampleVersion] },
            }

            const action = encryptAndUpdateVersionAction.done({
                params: { ...newVersionData, collectionId: "other-collection", fileId: exampleFileData.id },
                result: {
                    ...exampleFileData,
                    versionId: "5fc516fd-aaeb-4256-8501-c1987455b2d0",
                    contentDigest: "806fb3130a8b191071bd63c707af33166e13186d78d5b9873f42fc5404968258",
                },
            })

            const result = filesReducer(state, action)

            expect(result.fileVersionsResult).toStrictEqual(state.fileVersionsResult)
        })

        it("should not update list on new version upload if file ID does not match", () => {
            const state: State = {
                ...defaultState,
                fileVersionsResult: { status: "FINISHED", params: listVersionsParams, data: [exampleVersion] },
            }

            const action = encryptAndUpdateVersionAction.done({
                params: { ...newVersionData, fileId: "other-file" },
                result: {
                    ...exampleFileData,
                    versionId: "5fc516fd-aaeb-4256-8501-c1987455b2d0",
                    contentDigest: "806fb3130a8b191071bd63c707af33166e13186d78d5b9873f42fc5404968258",
                },
            })

            const result = filesReducer(state, action)

            expect(result.fileVersionsResult).toStrictEqual(state.fileVersionsResult)
        })

        it("should update list on version delete", () => {
            const version2 = {
                ...exampleVersion,
                versionId: "5fc516fd-aaeb-4256-8501-c1987455b2d0",
                contentDigest: "806fb3130a8b191071bd63c707af33166e13186d78d5b9873f42fc5404968258",
            }

            const state: State = {
                ...defaultState,
                fileVersionsResult: {
                    status: "FINISHED",
                    params: listVersionsParams,
                    data: [version2, exampleVersion],
                },
            }

            const action = deleteVersionAction.done({ params: deleteVersionParams, result: undefined })

            const result = filesReducer(state, action)

            expect(result.fileVersionsResult).toStrictEqual({
                status: "FINISHED",
                params: {
                    collectionId: "1bffed24-f1a0-4108-839d-5741846a8606",
                    fileId: "99de9649-ea1c-4002-95e0-18a66cdda224",
                },
                data: [version2],
            })
        })

        it("should not update list on version delete if state status is not FINISHED", () => {
            const state: State = {
                ...defaultState,
                fileVersionsResult: { status: "NOT_STARTED" },
            }

            const action = deleteVersionAction.done({ params: deleteVersionParams, result: undefined })

            const result = filesReducer(state, action)

            expect(result.fileVersionsResult).toStrictEqual(state.fileVersionsResult)
        })

        it("should not update list on version delete if collection ID does not match", () => {
            const version2 = {
                ...exampleVersion,
                versionId: "5fc516fd-aaeb-4256-8501-c1987455b2d0",
                contentDigest: "806fb3130a8b191071bd63c707af33166e13186d78d5b9873f42fc5404968258",
            }

            const state: State = {
                ...defaultState,
                fileVersionsResult: {
                    status: "FINISHED",
                    params: listVersionsParams,
                    data: [version2, exampleVersion],
                },
            }

            const action = deleteVersionAction.done({
                params: { ...deleteVersionParams, collectionId: "other-collection" },
                result: undefined,
            })

            const result = filesReducer(state, action)

            expect(result.fileVersionsResult).toStrictEqual(state.fileVersionsResult)
        })

        it("should not update list on version delete if file ID does not match", () => {
            const version2 = {
                ...exampleVersion,
                versionId: "5fc516fd-aaeb-4256-8501-c1987455b2d0",
                contentDigest: "806fb3130a8b191071bd63c707af33166e13186d78d5b9873f42fc5404968258",
            }

            const state: State = {
                ...defaultState,
                fileVersionsResult: {
                    status: "FINISHED",
                    params: listVersionsParams,
                    data: [version2, exampleVersion],
                },
            }

            const action = deleteVersionAction.done({
                params: { ...deleteVersionParams, fileId: "other-file" },
                result: undefined,
            })

            const result = filesReducer(state, action)

            expect(result.fileVersionsResult).toStrictEqual(state.fileVersionsResult)
        })
    })
})
