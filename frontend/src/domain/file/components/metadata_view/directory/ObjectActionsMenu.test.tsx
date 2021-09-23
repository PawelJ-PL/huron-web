import { fireEvent, render, screen } from "@testing-library/react"
import React from "react"
import { exampleCollectionId, exampleEncryptionKey } from "../../../../../testutils/constants/collection"
import { exampleFileData } from "../../../../../testutils/constants/files"
import { examplePrivateKey } from "../../../../../testutils/constants/user"
import { tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { ObjectActionsMenu } from "./ObjectActionsMenu"

describe("Object actions menu", () => {
    it("should request file rename on click", () => {
        const renameMock = jest.fn()

        render(
            <ObjectActionsMenu
                fullSize={true}
                metadata={exampleFileData}
                t={tFunctionMock}
                requestRename={renameMock}
                requestDelete={jest.fn()}
                requestVersionUpdate={jest.fn()}
                downloadFile={jest.fn()}
                encryptionKeyResult={{ status: "NOT_STARTED" }}
            />
        )

        const renameItem = screen.getByText("file-view:directory-content-list.file-actions.rename")
        fireEvent.click(renameItem)

        expect(renameMock).toHaveBeenCalledTimes(1)
        expect(renameMock).toHaveBeenCalledWith(exampleFileData)
    })

    it("should request file delete on click", () => {
        const deleteMock = jest.fn()

        render(
            <ObjectActionsMenu
                fullSize={true}
                metadata={exampleFileData}
                t={tFunctionMock}
                requestRename={jest.fn()}
                requestDelete={deleteMock}
                requestVersionUpdate={jest.fn()}
                downloadFile={jest.fn()}
                encryptionKeyResult={{ status: "NOT_STARTED" }}
            />
        )

        const deleteItem = screen.getByText("file-view:directory-content-list.file-actions.delete-file")
        fireEvent.click(deleteItem)

        expect(deleteMock).toHaveBeenCalledTimes(1)
        expect(deleteMock).toHaveBeenCalledWith(exampleFileData)
    })

    it("should request version update on click", () => {
        const versionUpdateMock = jest.fn()

        render(
            <ObjectActionsMenu
                fullSize={true}
                metadata={exampleFileData}
                t={tFunctionMock}
                requestRename={jest.fn()}
                requestDelete={jest.fn()}
                requestVersionUpdate={versionUpdateMock}
                downloadFile={jest.fn()}
                encryptionKeyResult={{ status: "NOT_STARTED" }}
            />
        )

        const updateItem = screen.getByText("file-view:directory-content-list.file-actions.upload-new-version")
        fireEvent.click(updateItem)

        expect(versionUpdateMock).toHaveBeenCalledTimes(1)
        expect(versionUpdateMock).toHaveBeenCalledWith(exampleFileData)
    })

    it("should request file download on click", () => {
        const downloadMock = jest.fn()

        render(
            <ObjectActionsMenu
                fullSize={true}
                metadata={exampleFileData}
                t={tFunctionMock}
                requestRename={jest.fn()}
                requestDelete={jest.fn()}
                requestVersionUpdate={jest.fn()}
                downloadFile={downloadMock}
                encryptionKeyResult={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                    data: exampleEncryptionKey,
                }}
            />
        )

        const downloadItem = screen.getByText("file-view:directory-content-list.file-actions.download-file")
        fireEvent.click(downloadItem)

        expect(downloadMock).toHaveBeenCalledTimes(1)
        expect(downloadMock).toHaveBeenCalledWith(exampleFileData, exampleEncryptionKey)
    })

    it("should disable download button if encryption key is not ready", () => {
        render(
            <ObjectActionsMenu
                fullSize={true}
                metadata={exampleFileData}
                t={tFunctionMock}
                requestRename={jest.fn()}
                requestDelete={jest.fn()}
                requestVersionUpdate={jest.fn()}
                downloadFile={jest.fn()}
                encryptionKeyResult={{
                    status: "PENDING",
                    params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                }}
            />
        )

        const downloadItem = screen.getByText("file-view:directory-content-list.file-actions.download-file")
        expect(downloadItem).toBeDisabled()
    })
})
