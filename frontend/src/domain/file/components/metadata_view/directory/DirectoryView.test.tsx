import { render, screen } from "@testing-library/react"
import FileSaver from "file-saver"
import React from "react"
import { exampleCollectionId, exampleEncryptionKey } from "../../../../../testutils/constants/collection"
import { exampleDirectoryData, exampleFileContent, exampleFileData } from "../../../../../testutils/constants/files"
import { tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { FileAlreadyExists, FileContentNotChanged, RecursivelyDelete } from "../../../api/errors"
import { FileDeleteFailed } from "../../../types/errors"
import { FileMetadata } from "../../../types/FilesystemUnitMetadata"
import { DirectoryView } from "./DirectoryView"

// eslint-disable-next-line react/display-name
jest.mock("./RenameModal", () => () => <div data-testid="RENAME_MODAL_MOCK"></div>)

// eslint-disable-next-line react/display-name
jest.mock("./DeleteFilesConfirmation", () => () => <div data-testid="DELETE_FILE_CONFIRMATION_MOCK"></div>)

// eslint-disable-next-line react/display-name
jest.mock("./UploadFileModal", () => (props: { uploadData: { fileMetadata: FileMetadata } }) => (
    <div data-testid="UPLOAD_FILE_MODAL_MOCK">{JSON.stringify(props.uploadData.fileMetadata)}</div>
))

// eslint-disable-next-line react/display-name
jest.mock("./ActionsPanel", () => () => <div data-testid="ACTIONS_PANEL_MOCK"></div>)

// eslint-disable-next-line react/display-name
jest.mock("./FullDirectoryList", () => () => <div data-testid="FULL_DIRECTORY_LIST_MOCK"></div>)

// eslint-disable-next-line react/display-name
jest.mock("./CompactDirectoryList", () => () => <div data-testid="COMPACT_DIRECTORY_LIST_MOCK"></div>)

jest.mock("../../../../../application/components/common/LoadingOverlay")

jest.mock("../../../../../application/components/common/AlertBox")

jest.mock("../../../../../application/components/common/UnexpectedErrorMessage")

jest.mock("../../../../../application/components/common/EmptyPlaceholder")

jest.mock("@chakra-ui/media-query", () => ({
    ...jest.requireActual("@chakra-ui/media-query"),
    useBreakpointValue: () => true,
}))

describe("Directory view", () => {
    describe("mount and unmount", () => {
        it("should reset rename result", () => {
            const resetRenameMock = jest.fn()

            const view = render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={resetRenameMock}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={exampleFileData}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            view.unmount()

            expect(resetRenameMock).toHaveBeenCalledTimes(2)
        })

        it("should reset delete result", () => {
            const resetDeleteMock = jest.fn()

            const view = render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={resetDeleteMock}
                    requestedVersionUpdate={exampleFileData}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            view.unmount()

            expect(resetDeleteMock).toHaveBeenCalledTimes(2)
        })

        it("should reset version update request", () => {
            const resetVersionUpdateRequestMock = jest.fn()

            const view = render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={exampleFileData}
                    resetVersionUpdateRequest={resetVersionUpdateRequestMock}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            view.unmount()

            expect(resetVersionUpdateRequestMock).toHaveBeenCalledTimes(2)
        })

        it("should reset version update result", () => {
            const resetVersionUpdateMock = jest.fn()

            const view = render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={exampleFileData}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={resetVersionUpdateMock}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            view.unmount()

            expect(resetVersionUpdateMock).toHaveBeenCalledTimes(2)
        })

        it("should reset download result", () => {
            const resetDownloadMock = jest.fn()

            const view = render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={exampleFileData}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={resetDownloadMock}
                />
            )

            view.unmount()

            expect(resetDownloadMock).toHaveBeenCalledTimes(2)
        })
    })

    it("should save file if download has completed", () => {
        const fileSaverSpy = jest.spyOn(FileSaver, "saveAs").mockReturnValue(undefined)

        const resetDownloadResult = jest.fn()

        render(
            <DirectoryView
                childObjects={[exampleDirectoryData, exampleFileData]}
                collectionId={exampleCollectionId}
                t={tFunctionMock}
                thisDirectoryId={null}
                renameResult={{ status: "NOT_STARTED" }}
                resetRenameResult={jest.fn()}
                deleteResult={{ status: "NOT_STARTED" }}
                resetDeleteResult={jest.fn()}
                requestedVersionUpdate={exampleFileData}
                resetVersionUpdateRequest={jest.fn()}
                versionUpdateResult={{ status: "NOT_STARTED" }}
                resetVersionUpdateResult={jest.fn()}
                fileDownloadResult={{
                    status: "FINISHED",
                    params: {
                        collectionId: exampleCollectionId,
                        fileId: exampleFileData.id,
                        encryptionKey: exampleEncryptionKey,
                    },
                    data: {
                        name: exampleFileData.name,
                        mimeType: exampleFileData.mimeType,
                        data: new Uint8Array([122, 123, 125, 128]),
                    },
                }}
                resetDownloadResult={resetDownloadResult}
            />
        )

        expect(resetDownloadResult).toHaveBeenCalledTimes(2)
        expect(fileSaverSpy).toHaveBeenCalledTimes(1)
        expect(fileSaverSpy).toHaveBeenCalledWith(
            new Blob([new Uint8Array([122, 123, 125, 128])]),
            exampleFileData.name
        )
        const saveBlob = fileSaverSpy.mock.calls[0][0] as Blob
        expect(saveBlob.type).toBe(exampleFileData.mimeType)
        expect(saveBlob.size).toBe(4) // https://github.com/jsdom/jsdom/issues/2555

        fileSaverSpy.mockRestore()
    })

    describe("loader", () => {
        it("should render loader if version update is in progress", () => {
            render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={exampleFileData}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{
                        status: "PENDING",
                        params: {
                            collectionId: exampleCollectionId,
                            fileId: exampleFileData.id,
                            latestVersionDigest: exampleFileData.contentDigest,
                            encryptionKey: exampleEncryptionKey,
                            file: exampleFileContent,
                        },
                    }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const loader = screen.getByText("common:action-in-progress")

            expect(loader).toHaveAttribute("aria-hidden", "false")
        })

        it("should render loader if download is in progress", () => {
            render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={exampleFileData}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{
                        status: "PENDING",
                        params: {
                            collectionId: exampleCollectionId,
                            fileId: exampleFileData.id,
                            encryptionKey: exampleEncryptionKey,
                        },
                    }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const loader = screen.getByText("file-view:directory-content-list.download-in-progress-message")

            expect(loader).toHaveAttribute("aria-hidden", "false")
        })
    })

    describe("upload file modal", () => {
        it("should be visible", () => {
            render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={exampleFileData}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const modal = screen.getByTestId("UPLOAD_FILE_MODAL_MOCK")

            expect(modal).toHaveTextContent(JSON.stringify(exampleFileData))
        })

        it("should be hidden", () => {
            render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={null}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const modal = screen.queryByTestId("UPLOAD_FILE_MODAL_MOCK")

            expect(modal).toBeNull()
        })
    })

    describe("render error", () => {
        it("should render unexpected error message if rename failed", () => {
            render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{
                        status: "FAILED",
                        params: {
                            collectionId: exampleCollectionId,
                            fileId: exampleFileData.id,
                            newName: exampleFileData.name,
                        },
                        error: new Error("Some error"),
                    }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={null}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const errorBox = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorBox).toHaveTextContent("Some error")
        })

        it("should render unexpected error message if delete failed", () => {
            render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{
                        status: "FAILED",
                        params: { collectionId: exampleCollectionId, fileIds: [exampleFileData.id] },
                        error: new Error("Some error"),
                    }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={null}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const errorBox = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorBox).toHaveTextContent("Some error")
        })

        it("should render unexpected error message if version update failed", () => {
            render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={null}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{
                        status: "FAILED",
                        params: {
                            collectionId: exampleCollectionId,
                            fileId: exampleFileData.id,
                            latestVersionDigest: exampleFileData.contentDigest,
                            file: exampleFileContent,
                            encryptionKey: exampleEncryptionKey,
                        },
                        error: new Error("Some error"),
                    }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const errorBox = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorBox).toHaveTextContent("Some error")
        })

        it("should render unexpected error message if download failed", () => {
            render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={null}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{
                        status: "FAILED",
                        params: {
                            collectionId: exampleCollectionId,
                            fileId: exampleFileData.id,
                            encryptionKey: exampleEncryptionKey,
                        },
                        error: new Error("Some error"),
                    }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const errorBox = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorBox).toHaveTextContent("Some error")
        })

        it("should render file already exists message", () => {
            render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{
                        status: "FAILED",
                        params: {
                            collectionId: exampleCollectionId,
                            fileId: exampleFileData.id,
                            newName: exampleFileData.name,
                        },
                        error: new FileAlreadyExists(exampleCollectionId, null, exampleFileData.name),
                    }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={null}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const errorBox = screen.getByTestId("ALERT_BOX_MOCK")
            expect(errorBox).toHaveTextContent("TITLE: file-view:directory-content-list.file-already-exists")
        })

        it("should render file content not changed error message", () => {
            render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={null}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{
                        status: "FAILED",
                        params: {
                            collectionId: exampleCollectionId,
                            fileId: exampleFileData.id,
                            latestVersionDigest: exampleFileData.contentDigest,
                            file: exampleFileContent,
                            encryptionKey: exampleEncryptionKey,
                        },
                        error: new FileContentNotChanged(exampleCollectionId, exampleFileData.id),
                    }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const errorBox = screen.getByTestId("ALERT_BOX_MOCK")
            expect(errorBox).toHaveTextContent("TITLE: file-view:directory-content-list.file-content-not-changed-error")
        })

        it("should render recursive delete error message", () => {
            render(
                <DirectoryView
                    childObjects={[exampleDirectoryData, exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{
                        status: "FAILED",
                        params: { collectionId: exampleCollectionId, fileIds: [exampleFileData.id] },
                        error: new FileDeleteFailed(
                            [],
                            [
                                {
                                    fileId: exampleFileData.id,
                                    error: new RecursivelyDelete(exampleCollectionId, exampleFileData.id),
                                },
                            ]
                        ),
                    }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={null}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const errorBox = screen.getByTestId("ALERT_BOX_MOCK")
            expect(errorBox).toHaveTextContent(
                "TITLE: file-view:directory-content-list.delete-recursively-not-allowed-error"
            )
        })
    })

    describe("Empty directory placeholder", () => {
        it("Should be rendered if directory has children", () => {
            render(
                <DirectoryView
                    childObjects={[]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={exampleFileData}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const placeholder = screen.getByTestId("EMPTY_PLACEHOLDER_MOCK")
            expect(placeholder).toHaveTextContent("file-view:directory-content-list.empty-directory-placeholder")
        })

        it("Should not be rendered if directory has no children", () => {
            render(
                <DirectoryView
                    childObjects={[exampleFileData]}
                    collectionId={exampleCollectionId}
                    t={tFunctionMock}
                    thisDirectoryId={null}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    requestedVersionUpdate={exampleFileData}
                    resetVersionUpdateRequest={jest.fn()}
                    versionUpdateResult={{ status: "NOT_STARTED" }}
                    resetVersionUpdateResult={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const placeholder = screen.queryByTestId("EMPTY_PLACEHOLDER_MOCK")
            expect(placeholder).toBeNull()
        })
    })
})
