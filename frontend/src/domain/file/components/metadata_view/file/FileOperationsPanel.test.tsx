import { fireEvent, screen } from "@testing-library/react"
import React from "react"
import {
    exampleChildFile1,
    exampleDirectoryData,
    exampleFileData,
    exampleFileId,
} from "../../../../../testutils/constants/files"
import { tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { FileOperationsPanel } from "./FileOperationsPanel"
import { exampleCollectionId, exampleEncryptionKey } from "../../../../../testutils/constants/collection"
import { FileDeleteFailed } from "../../../types/errors"
import FileSaver from "file-saver"
import { examplePrivateKey } from "../../../../../testutils/constants/user"
import { renderWithRoute } from "../../../../../testutils/helpers"

const startRoute = `/collection/${exampleCollectionId}/file/${exampleFileData}`
const pathTemplate = "/collection/:collectionId/file/:fileId"

// eslint-disable-next-line testing-library/render-result-naming-convention
const renderWithPath = renderWithRoute(pathTemplate)

// eslint-disable-next-line react/display-name
jest.mock("../directory/RenameModal", () => () => <div data-testid="RENAME_MODAL_MOCK"></div>)

// eslint-disable-next-line react/display-name
jest.mock("../directory/DeleteFilesConfirmation", () => () => <div data-testid="DELETE_FILE_CONFIRMATION_MOCK"></div>)

// eslint-disable-next-line react/display-name
jest.mock("../directory/UploadFileModal", () => (props: { isOpen: boolean }) => (
    <div aria-hidden={!props.isOpen} data-testid="UPLOAD_FILE_MODAL_MOCK"></div>
))

jest.mock("../../../../../application/components/common/LoadingOverlay")

jest.mock("../../../../../application/components/common/AlertBox")

describe("File operations panel", () => {
    beforeEach(() => window.history.replaceState({}, "", startRoute))

    it("should reset all results on mount and unmount", () => {
        const resetAllMock = jest.fn()

        const view = renderWithPath(
            <FileOperationsPanel
                t={tFunctionMock}
                file={exampleFileData}
                encryptionKeyResult={{ status: "NOT_STARTED" }}
                renameFile={jest.fn()}
                resetAllActionsResults={resetAllMock}
                actionInProgress={false}
                deleteFile={jest.fn()}
                deleteResult={{ status: "NOT_STARTED" }}
                downloadFile={jest.fn()}
                fileDownloadResult={{ status: "NOT_STARTED" }}
                resetDownloadResult={jest.fn()}
            />
        )

        view.unmount()

        expect(resetAllMock).toHaveBeenCalledTimes(2)
    })

    describe("redirect on delete", () => {
        it("should redirect on delete success if file ID is in deleted array", () => {
            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{ status: "NOT_STARTED" }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileIds: [exampleFileData.id] },
                        data: undefined,
                    }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            expect(window.location.pathname).toBe(`/collection/${exampleCollectionId}`)
        })

        it("should not redirect on delete success if file ID not in deleted array", () => {
            const historyPushMock = jest.fn()

            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{ status: "NOT_STARTED" }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileIds: ["other-file"] },
                        data: undefined,
                    }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            expect(historyPushMock).not.toHaveBeenCalled()
        })

        it("should redirect on delete failed if file ID is in deleted array", () => {
            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{ status: "NOT_STARTED" }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{
                        status: "FAILED",
                        params: { collectionId: exampleCollectionId, fileIds: [exampleFileData.id] },
                        error: new FileDeleteFailed(
                            [exampleFileData.id, exampleDirectoryData.id],
                            [{ fileId: exampleChildFile1.id, error: new Error("Some error") }]
                        ),
                    }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            expect(window.location.pathname).toBe(`/collection/${exampleCollectionId}`)
        })

        it("should not redirect on delete failed if file ID not in deleted array", () => {
            const historyPushMock = jest.fn()

            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{ status: "NOT_STARTED" }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{
                        status: "FAILED",
                        params: { collectionId: exampleCollectionId, fileIds: [exampleFileData.id] },
                        error: new FileDeleteFailed(
                            [exampleDirectoryData.id],
                            [{ fileId: exampleChildFile1.id, error: new Error("Some error") }]
                        ),
                    }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            expect(historyPushMock).not.toHaveBeenCalled()
        })
    })

    it("should save file on download", () => {
        const fileSaverSpy = jest.spyOn(FileSaver, "saveAs").mockReturnValue(undefined)

        const resetDownloadResetMock = jest.fn()

        renderWithPath(
            <FileOperationsPanel
                t={tFunctionMock}
                file={exampleFileData}
                encryptionKeyResult={{ status: "NOT_STARTED" }}
                renameFile={jest.fn()}
                resetAllActionsResults={jest.fn()}
                actionInProgress={false}
                deleteFile={jest.fn()}
                deleteResult={{ status: "NOT_STARTED" }}
                downloadFile={jest.fn()}
                fileDownloadResult={{
                    status: "FINISHED",
                    params: {
                        collectionId: exampleCollectionId,
                        fileId: exampleFileId,
                        encryptionKey: exampleEncryptionKey,
                    },
                    data: {
                        name: exampleFileData.name,
                        mimeType: exampleFileData.mimeType,
                        data: new Uint8Array([122, 123, 125, 128]),
                    },
                }}
                resetDownloadResult={resetDownloadResetMock}
            />
        )

        expect(resetDownloadResetMock).toHaveBeenCalledTimes(1)
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
        it("should be hidden if no action is in progress", () => {
            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{ status: "NOT_STARTED" }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const loader = screen.getByTestId("LOADING_OVERLAY_MOCK")
            expect(loader).toHaveAttribute("aria-hidden", "true")
        })

        it("should be visible if action is in progress", () => {
            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{ status: "NOT_STARTED" }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={true}
                    deleteFile={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const loader = screen.getByTestId("LOADING_OVERLAY_MOCK")
            expect(loader).toHaveAttribute("aria-hidden", "false")
        })
    })

    describe("locked key warning", () => {
        it("should be visible if key is locked", () => {
            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{ status: "NOT_STARTED" }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const warningBox = screen.getByTestId("ALERT_BOX_MOCK")
            expect(warningBox).toHaveTextContent("TITLE: file-view:file-data.download-upload-unavailable")
        })

        it("should be hidden if key is unlocked", () => {
            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                        data: exampleEncryptionKey,
                    }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const warningBox = screen.queryByTestId("ALERT_BOX_MOCK")
            expect(warningBox).not.toBeInTheDocument()
        })
    })

    describe("download button", () => {
        it("should be disabled if key is locked", () => {
            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{ status: "NOT_STARTED" }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const button = screen.getByText("File-view:directory-content-list.file-actions.download-file")
            expect(button).toBeDisabled()
        })

        it("should be enabled if key is unlocked", () => {
            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                        data: exampleEncryptionKey,
                    }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const button = screen.getByText("File-view:directory-content-list.file-actions.download-file")
            expect(button).toBeEnabled()
        })

        it("should trigger file download on click", () => {
            const downloadFileMock = jest.fn()

            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                        data: exampleEncryptionKey,
                    }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={downloadFileMock}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const button = screen.getByText("File-view:directory-content-list.file-actions.download-file")
            fireEvent.click(button)

            expect(downloadFileMock).toHaveBeenCalledTimes(1)
        })
    })

    describe("version upload button", () => {
        it("should be disabled if key is locked", () => {
            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{ status: "NOT_STARTED" }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const button = screen.getByText("File-view:directory-content-list.file-actions.upload-new-version")
            expect(button).toBeDisabled()
        })

        it("should be enabled if key is unlocked", () => {
            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                        data: exampleEncryptionKey,
                    }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const button = screen.getByText("File-view:directory-content-list.file-actions.upload-new-version")
            expect(button).toBeEnabled()
        })

        it("should open modal on click", () => {
            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                        data: exampleEncryptionKey,
                    }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const modal = screen.getByTestId("UPLOAD_FILE_MODAL_MOCK")
            expect(modal).toHaveAttribute("aria-hidden", "true")

            const button = screen.getByText("File-view:directory-content-list.file-actions.upload-new-version")
            fireEvent.click(button)

            expect(modal).toHaveAttribute("aria-hidden", "false")
        })
    })

    describe("rename button", () => {
        it("should trigger rename on click", () => {
            const renameMock = jest.fn()

            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{ status: "NOT_STARTED" }}
                    renameFile={renameMock}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const button = screen.getByText("File-view:directory-content-list.file-actions.rename")
            fireEvent.click(button)

            expect(renameMock).toHaveBeenCalledTimes(1)
            expect(renameMock).toHaveBeenCalledWith(exampleFileData)
        })
    })

    describe("delete button", () => {
        it("should trigger delete on click", () => {
            const deleteMock = jest.fn()

            renderWithPath(
                <FileOperationsPanel
                    t={tFunctionMock}
                    file={exampleFileData}
                    encryptionKeyResult={{ status: "NOT_STARTED" }}
                    renameFile={jest.fn()}
                    resetAllActionsResults={jest.fn()}
                    actionInProgress={false}
                    deleteFile={deleteMock}
                    deleteResult={{ status: "NOT_STARTED" }}
                    downloadFile={jest.fn()}
                    fileDownloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                />
            )

            const button = screen.getByText("Common:delete-imperative")
            fireEvent.click(button)

            expect(deleteMock).toHaveBeenCalledTimes(1)
            expect(deleteMock).toHaveBeenCalledWith(exampleFileData)
        })
    })
})
