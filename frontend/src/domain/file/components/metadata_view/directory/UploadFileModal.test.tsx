import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import React from "react"
import { exampleCollectionId, exampleEncryptionKey } from "../../../../../testutils/constants/collection"
import { exampleFileContent, exampleFileData } from "../../../../../testutils/constants/files"
import { examplePrivateKey } from "../../../../../testutils/constants/user"
import { i18nMock, tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { UPLOAD_FILE_INPUT } from "../../testids"
import { UploadFileModal } from "./UploadFileModal"

jest.mock("../../../../../application/components/common/AlertBox")

describe("Upload file modal", () => {
    it("should render message if encryption key is locked", () => {
        render(
            <UploadFileModal
                isOpen={true}
                onClose={jest.fn()}
                uploadData={{ type: "NewFile", collectionId: exampleCollectionId, parent: null }}
                t={tFunctionMock}
                i18n={i18nMock()}
                encryptionKey={{ status: "NOT_STARTED" }}
                encryptAndUpdateVersion={jest.fn()}
                encryptAndUploadNewFile={jest.fn()}
            />
        )

        const errorMessage = screen.queryByText(
            "TITLE: file-view:directory-content-list.missing-encryption-key-message"
        )
        const dropzone = screen.queryByText("file-view:directory-content-list.dropzone-content")
        expect(errorMessage).toBeTruthy()
        expect(dropzone).toBeNull()
    })

    it("should render dropzone if encryption key is unlocked", () => {
        render(
            <UploadFileModal
                isOpen={true}
                onClose={jest.fn()}
                uploadData={{ type: "NewFile", collectionId: exampleCollectionId, parent: null }}
                t={tFunctionMock}
                i18n={i18nMock()}
                encryptionKey={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                    data: exampleEncryptionKey,
                }}
                encryptAndUpdateVersion={jest.fn()}
                encryptAndUploadNewFile={jest.fn()}
            />
        )

        const errorMessage = screen.queryByText("file-view:directory-content-list.missing-encryption-key-message")
        const dropzone = screen.queryByText("file-view:directory-content-list.dropzone-content")
        expect(errorMessage).toBeNull()
        expect(dropzone).toBeTruthy()
    })

    it("should show loaded file name", async () => {
        render(
            <UploadFileModal
                isOpen={true}
                onClose={jest.fn()}
                uploadData={{ type: "NewFile", collectionId: exampleCollectionId, parent: null }}
                t={tFunctionMock}
                i18n={i18nMock()}
                encryptionKey={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                    data: exampleEncryptionKey,
                }}
                encryptAndUpdateVersion={jest.fn()}
                encryptAndUploadNewFile={jest.fn()}
            />
        )

        const uploadInput = screen.getByTestId(UPLOAD_FILE_INPUT)
        fireEvent.change(uploadInput, { target: { files: [exampleFileContent] } })

        await waitFor(() => {
            const alertBox = screen.getByTestId("ALERT_BOX_MOCK")
            expect(alertBox).toHaveTextContent(`TITLE: ${exampleFileContent.name}`)
        })
    })

    it("should render too many files error", async () => {
        render(
            <UploadFileModal
                isOpen={true}
                onClose={jest.fn()}
                uploadData={{ type: "NewFile", collectionId: exampleCollectionId, parent: null }}
                t={tFunctionMock}
                i18n={i18nMock()}
                encryptionKey={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                    data: exampleEncryptionKey,
                }}
                encryptAndUpdateVersion={jest.fn()}
                encryptAndUploadNewFile={jest.fn()}
            />
        )

        const uploadInput = screen.getByTestId(UPLOAD_FILE_INPUT)
        fireEvent.change(uploadInput, { target: { files: [exampleFileContent, exampleFileContent] } })

        await waitFor(() => {
            const alertBox = screen.getByTestId("ALERT_BOX_MOCK")
            expect(alertBox).toHaveTextContent("TITLE: file-view:directory-content-list.load-file-error")
        })
    })

    it("should upload new file", async () => {
        const uploadFileMock = jest.fn()
        const uploadVersionMock = jest.fn()
        const closeMock = jest.fn()

        render(
            <UploadFileModal
                isOpen={true}
                onClose={closeMock}
                uploadData={{ type: "NewFile", collectionId: exampleCollectionId, parent: null }}
                t={tFunctionMock}
                i18n={i18nMock()}
                encryptionKey={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                    data: exampleEncryptionKey,
                }}
                encryptAndUpdateVersion={uploadVersionMock}
                encryptAndUploadNewFile={uploadFileMock}
            />
        )

        const uploadInput = screen.getByTestId(UPLOAD_FILE_INPUT)
        // eslint-disable-next-line testing-library/no-wait-for-side-effects
        await waitFor(() => fireEvent.change(uploadInput, { target: { files: [exampleFileContent] } }))

        const confirmButton = screen.getByText("file-view:directory-content-list.encrypt-and-send")
        await userEvent.click(confirmButton)

        expect(closeMock).toHaveBeenCalledTimes(1)
        expect(uploadFileMock).toHaveBeenCalledTimes(1)
        expect(uploadFileMock).toHaveBeenCalledWith(exampleCollectionId, null, exampleFileContent, exampleEncryptionKey)
        expect(uploadVersionMock).not.toHaveBeenCalled()
    })

    it("should upload new version", async () => {
        const uploadFileMock = jest.fn()
        const uploadVersionMock = jest.fn()
        const closeMock = jest.fn()

        render(
            <UploadFileModal
                isOpen={true}
                onClose={closeMock}
                uploadData={{ type: "NewVersion", fileMetadata: exampleFileData }}
                t={tFunctionMock}
                i18n={i18nMock()}
                encryptionKey={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                    data: exampleEncryptionKey,
                }}
                encryptAndUpdateVersion={uploadVersionMock}
                encryptAndUploadNewFile={uploadFileMock}
            />
        )

        const uploadInput = screen.getByTestId(UPLOAD_FILE_INPUT)
        // eslint-disable-next-line testing-library/no-wait-for-side-effects
        await waitFor(() => fireEvent.change(uploadInput, { target: { files: [exampleFileContent] } }))

        const confirmButton = screen.getByText("file-view:directory-content-list.encrypt-and-send")
        await userEvent.click(confirmButton)

        expect(closeMock).toHaveBeenCalledTimes(1)
        expect(uploadFileMock).not.toHaveBeenCalled()
        expect(uploadVersionMock).toHaveBeenCalledTimes(1)
        expect(uploadVersionMock).toHaveBeenCalledWith(exampleFileData, exampleFileContent, exampleEncryptionKey)
    })

    it("should close modal on cancel", async () => {
        const uploadFileMock = jest.fn()
        const uploadVersionMock = jest.fn()
        const closeMock = jest.fn()

        render(
            <UploadFileModal
                isOpen={true}
                onClose={closeMock}
                uploadData={{ type: "NewVersion", fileMetadata: exampleFileData }}
                t={tFunctionMock}
                i18n={i18nMock()}
                encryptionKey={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                    data: exampleEncryptionKey,
                }}
                encryptAndUpdateVersion={uploadVersionMock}
                encryptAndUploadNewFile={uploadFileMock}
            />
        )

        const uploadInput = screen.getByTestId(UPLOAD_FILE_INPUT)
        // eslint-disable-next-line testing-library/no-wait-for-side-effects
        await waitFor(() => fireEvent.change(uploadInput, { target: { files: [exampleFileContent] } }))

        const cancelButton = screen.getByText("Common:cancel-imperative")
        fireEvent.click(cancelButton)

        expect(closeMock).toHaveBeenCalledTimes(1)
        expect(uploadFileMock).not.toHaveBeenCalled()
        expect(uploadVersionMock).not.toHaveBeenCalled()
    })
})
