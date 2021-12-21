import { fireEvent, render, screen } from "@testing-library/react"
import React from "react"
import { exampleCollectionId, exampleEncryptionKey } from "../../../../../testutils/constants/collection"
import {
    exampleDirectoryData,
    exampleDirectoryId,
    exampleFileContent,
    exampleFileData,
} from "../../../../../testutils/constants/files"
import { i18nMock, tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { EncryptedFileTooLarge, FileAlreadyExists } from "../../../api/errors"
import { ActionsPanel } from "./ActionsPanel"

jest.mock("../../../../../application/components/common/UnexpectedErrorMessage")

jest.mock("../../../../../application/components/common/AlertBox")

jest.mock("../../../../../application/components/common/LoadingOverlay")

// eslint-disable-next-line react/display-name
jest.mock("./NewDirectoryModal", () => (props: { isOpen: boolean; parent: string | null; collectionId: string }) => (
    <div data-testid="NEW_DIRECTORY_MODAL_MOCK" aria-hidden={!props.isOpen}>
        <div>parent: {JSON.stringify(props.parent)} </div>
        <div>collection: {props.collectionId}</div>
    </div>
))

jest.mock(
    "./UploadFileModal",
    // eslint-disable-next-line react/display-name
    () => (props: { isOpen: boolean; uploadData: { collectionId: string; parent: string | null } }) =>
        (
            <div aria-hidden={!props.isOpen} data-testid="UPLOAD_FILE_MODAL">
                <div>Collection: {props.uploadData.collectionId}</div>
                <div>Parent: {JSON.stringify(props.uploadData.parent)}</div>
            </div>
        )
)

describe("Actions panel", () => {
    describe("render error", () => {
        it("should render error on directory creation failed because of generic error", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{
                        status: "FAILED",
                        params: { collectionId: exampleCollectionId, parent: null, name: exampleDirectoryData.name },
                        error: new Error("Some error"),
                    }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const errorBox = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorBox).toHaveTextContent("Some error")
        })

        it("should render error on directory creation failed because file already exists", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{
                        status: "FAILED",
                        params: { collectionId: exampleCollectionId, parent: null, name: exampleDirectoryData.name },
                        error: new FileAlreadyExists(exampleCollectionId, null, exampleDirectoryData.name),
                    }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const errorBox = screen.getByTestId("ALERT_BOX_MOCK")
            expect(errorBox).toHaveTextContent("TITLE: file-view:directory-content-list.file-already-exists")
        })

        it("should render error on file upload failed because of generic error", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{
                        status: "FAILED",
                        params: {
                            collectionId: exampleCollectionId,
                            parent: null,
                            file: exampleFileContent,
                            encryptionKey: exampleEncryptionKey,
                        },
                        error: new Error("Some error"),
                    }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const errorBox = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorBox).toHaveTextContent("Some error")
        })

        it("should render error on file upload failed because file is too large", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{
                        status: "FAILED",
                        params: {
                            collectionId: exampleCollectionId,
                            parent: null,
                            file: exampleFileContent,
                            encryptionKey: exampleEncryptionKey,
                        },
                        error: new EncryptedFileTooLarge(5000, 300),
                    }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const errorBox = screen.getByTestId("ALERT_BOX_MOCK")
            expect(errorBox).toHaveTextContent("TITLE: file-view:directory-content-list.encrypted file to large")
        })
    })

    describe("render loader", () => {
        it("should hide loader", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const loader = screen.getByTestId("LOADING_OVERLAY_MOCK")
            expect(loader).toHaveAttribute("aria-hidden", "true")
        })

        it("should show loader if action is in progress", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={true}
                    createDirResult={{
                        status: "PENDING",
                        params: { collectionId: exampleCollectionId, parent: null, name: exampleDirectoryData.name },
                    }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const loader = screen.getByTestId("LOADING_OVERLAY_MOCK")
            expect(loader).toHaveAttribute("aria-hidden", "false")
            expect(loader).toHaveTextContent("common:action-in-progress")
        })
    })

    describe("new directory modal", () => {
        it("should be closed by default", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const modal = screen.getByTestId("NEW_DIRECTORY_MODAL_MOCK")
            expect(modal).toHaveAttribute("aria-hidden", "true")
        })

        it("should be open after button click", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const button = screen.getByText("file-view:directory-content-list.create-directory")
            const modal = screen.getByTestId("NEW_DIRECTORY_MODAL_MOCK")
            expect(modal).toHaveAttribute("aria-hidden", "true")

            fireEvent.click(button)

            expect(modal).toHaveAttribute("aria-hidden", "false")
            expect(modal).toHaveTextContent(`parent: "${exampleDirectoryId}"`)
            expect(modal).toHaveTextContent(`collection: ${exampleCollectionId}`)
        })
    })

    describe("upload file modal", () => {
        it("should be closed by default", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const modal = screen.getByTestId("UPLOAD_FILE_MODAL")
            expect(modal).toHaveAttribute("aria-hidden", "true")
        })

        it("should be open after button click", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const button = screen.getByText("file-view:directory-content-list.upload-file-button-label")
            const modal = screen.getByTestId("UPLOAD_FILE_MODAL")
            expect(modal).toHaveAttribute("aria-hidden", "true")

            fireEvent.click(button)

            expect(modal).toHaveAttribute("aria-hidden", "false")
            expect(modal).toHaveTextContent(`Parent: "${exampleDirectoryId}"`)
            expect(modal).toHaveTextContent(`Collection: ${exampleCollectionId}`)
        })
    })

    describe("delete button", () => {
        it("should be disabled if no file is selected", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const button = screen.getByText("Common:delete-imperative")
            expect(button).toBeDisabled()
        })

        it("should be enabled if some files are selected", () => {
            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[exampleFileData]}
                    requestFilesDelete={jest.fn()}
                />
            )

            const button = screen.getByText("Common:delete-imperative")
            expect(button).toBeEnabled()
        })

        it("should trigger action on click", () => {
            const requestDeleteMock = jest.fn()

            render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[exampleFileData]}
                    requestFilesDelete={requestDeleteMock}
                />
            )

            expect(requestDeleteMock).not.toHaveBeenCalled()

            const button = screen.getByText("Common:delete-imperative")

            fireEvent.click(button)

            expect(requestDeleteMock).toHaveBeenCalledTimes(1)
            expect(requestDeleteMock).toHaveBeenCalledWith([exampleFileData])
        })
    })

    describe("umount", () => {
        it("should trigger reset create dir result", () => {
            const resetCreateDirResultMock = jest.fn()

            const view = render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={resetCreateDirResultMock}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[exampleFileData]}
                    requestFilesDelete={jest.fn()}
                />
            )

            view.unmount()

            expect(resetCreateDirResultMock).toHaveBeenCalledTimes(1)
        })

        it("should trigger reset upload result", () => {
            const resetUploadResultMock = jest.fn()

            const view = render(
                <ActionsPanel
                    collectionId={exampleCollectionId}
                    thisDirectoryId={exampleDirectoryId}
                    actionInProgress={false}
                    createDirResult={{ status: "NOT_STARTED" }}
                    resetCreateDirResult={jest.fn()}
                    uploadFileResult={{ status: "NOT_STARTED" }}
                    resetUploadFileResult={resetUploadResultMock}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    selectedFiles={[exampleFileData]}
                    requestFilesDelete={jest.fn()}
                />
            )

            view.unmount()

            expect(resetUploadResultMock).toHaveBeenCalledTimes(1)
        })
    })
})
