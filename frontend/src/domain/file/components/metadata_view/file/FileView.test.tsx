import { render, screen } from "@testing-library/react"
import React from "react"
import { exampleCollectionId, exampleEncryptionKey } from "../../../../../testutils/constants/collection"
import { exampleFileContent, exampleFileData, exampleVersionId } from "../../../../../testutils/constants/files"
import { i18nMock, tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { FileAlreadyExists, FileContentNotChanged } from "../../../api/errors"
import { FileView } from "./FileView"

// eslint-disable-next-line react/display-name
jest.mock("./Author", () => () => <div data-testid="AUTHOR_MOCK"></div>)

// eslint-disable-next-line react/display-name
jest.mock("./FileOperationsPanel", () => () => <div data-testid="FILE_OPERATIONS_PANEL_MOCK"></div>)

// eslint-disable-next-line react/display-name
jest.mock("./VersionsList", () => () => <div data-testid="VERSIONS_LIST_MOCK"></div>)

jest.mock("../../../../../application/components/common/UnexpectedErrorMessage")

jest.mock("../../../../../application/components/common/AlertBox")

describe("File view", () => {
    it("should reset all on mount and unmount", () => {
        const resetAllMock = jest.fn()

        const elem = render(
            <FileView
                metadata={exampleFileData}
                i18n={i18nMock()}
                t={tFunctionMock}
                deleteResult={{ status: "NOT_STARTED" }}
                resetDeleteResult={jest.fn()}
                renameResult={{ status: "NOT_STARTED" }}
                resetRenameResult={jest.fn()}
                updateResult={{ status: "NOT_STARTED" }}
                resetUpdateResult={jest.fn()}
                downloadResult={{ status: "NOT_STARTED" }}
                resetDownloadResult={jest.fn()}
                deleteVersionResult={{ status: "NOT_STARTED" }}
                resetDeleteVersionResult={jest.fn()}
                resetAll={resetAllMock}
            />
        )

        elem.unmount()

        expect(resetAllMock).toHaveBeenCalledTimes(2)
    })

    describe("render error", () => {
        it("should render unexpected error message on delete failed", () => {
            render(
                <FileView
                    metadata={exampleFileData}
                    i18n={i18nMock()}
                    t={tFunctionMock}
                    deleteResult={{
                        status: "FAILED",
                        params: { collectionId: exampleCollectionId, fileIds: [exampleFileData.id] },
                        error: new Error("delete error"),
                    }}
                    resetDeleteResult={jest.fn()}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    updateResult={{ status: "NOT_STARTED" }}
                    resetUpdateResult={jest.fn()}
                    downloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                    deleteVersionResult={{ status: "NOT_STARTED" }}
                    resetDeleteVersionResult={jest.fn()}
                    resetAll={jest.fn()}
                />
            )

            const errorMessage = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorMessage).toHaveTextContent("delete error")
        })

        it("should render unexpected error message on rename failed", () => {
            render(
                <FileView
                    metadata={exampleFileData}
                    i18n={i18nMock()}
                    t={tFunctionMock}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    renameResult={{
                        status: "FAILED",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id, newName: "some-name" },
                        error: new Error("rename error"),
                    }}
                    resetRenameResult={jest.fn()}
                    updateResult={{ status: "NOT_STARTED" }}
                    resetUpdateResult={jest.fn()}
                    downloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                    deleteVersionResult={{ status: "NOT_STARTED" }}
                    resetDeleteVersionResult={jest.fn()}
                    resetAll={jest.fn()}
                />
            )

            const errorMessage = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorMessage).toHaveTextContent("rename error")
        })

        it("should render unexpected error message on update failed", () => {
            render(
                <FileView
                    metadata={exampleFileData}
                    i18n={i18nMock()}
                    t={tFunctionMock}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    updateResult={{
                        status: "FAILED",
                        params: {
                            collectionId: exampleCollectionId,
                            fileId: exampleFileData.id,
                            latestVersionDigest: exampleFileData.contentDigest,
                            file: exampleFileContent,
                            encryptionKey: exampleEncryptionKey,
                        },
                        error: new Error("update error"),
                    }}
                    resetUpdateResult={jest.fn()}
                    downloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                    deleteVersionResult={{ status: "NOT_STARTED" }}
                    resetDeleteVersionResult={jest.fn()}
                    resetAll={jest.fn()}
                />
            )

            const errorMessage = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorMessage).toHaveTextContent("update error")
        })

        it("should render unexpected error message on download failed", () => {
            render(
                <FileView
                    metadata={exampleFileData}
                    i18n={i18nMock()}
                    t={tFunctionMock}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    updateResult={{ status: "NOT_STARTED" }}
                    resetUpdateResult={jest.fn()}
                    downloadResult={{
                        status: "FAILED",
                        params: {
                            collectionId: exampleCollectionId,
                            fileId: exampleFileData.id,
                            encryptionKey: exampleEncryptionKey,
                        },
                        error: new Error("download error"),
                    }}
                    resetDownloadResult={jest.fn()}
                    deleteVersionResult={{ status: "NOT_STARTED" }}
                    resetDeleteVersionResult={jest.fn()}
                    resetAll={jest.fn()}
                />
            )

            const errorMessage = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorMessage).toHaveTextContent("download error")
        })

        it("should render unexpected error message on delete version failed", () => {
            render(
                <FileView
                    metadata={exampleFileData}
                    i18n={i18nMock()}
                    t={tFunctionMock}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    updateResult={{ status: "NOT_STARTED" }}
                    resetUpdateResult={jest.fn()}
                    downloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                    deleteVersionResult={{
                        status: "FAILED",
                        params: {
                            collectionId: exampleCollectionId,
                            fileId: exampleFileData.id,
                            versionId: exampleVersionId,
                        },
                        error: new Error("delete version error"),
                    }}
                    resetDeleteVersionResult={jest.fn()}
                    resetAll={jest.fn()}
                />
            )

            const errorMessage = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorMessage).toHaveTextContent("delete version error")
        })

        it("should render file already exists error", () => {
            render(
                <FileView
                    metadata={exampleFileData}
                    i18n={i18nMock()}
                    t={tFunctionMock}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    renameResult={{
                        status: "FAILED",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id, newName: "some-name" },
                        error: new FileAlreadyExists(
                            exampleCollectionId,
                            exampleFileData.parent ?? null,
                            exampleFileData.name
                        ),
                    }}
                    resetRenameResult={jest.fn()}
                    updateResult={{ status: "NOT_STARTED" }}
                    resetUpdateResult={jest.fn()}
                    downloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                    deleteVersionResult={{ status: "NOT_STARTED" }}
                    resetDeleteVersionResult={jest.fn()}
                    resetAll={jest.fn()}
                />
            )

            const errorMessage = screen.getByTestId("ALERT_BOX_MOCK")
            expect(errorMessage).toHaveTextContent("TITLE: file-view:directory-content-list.file-already-exists")
        })

        it("should render file content not changed error", () => {
            render(
                <FileView
                    metadata={exampleFileData}
                    i18n={i18nMock()}
                    t={tFunctionMock}
                    deleteResult={{ status: "NOT_STARTED" }}
                    resetDeleteResult={jest.fn()}
                    renameResult={{ status: "NOT_STARTED" }}
                    resetRenameResult={jest.fn()}
                    updateResult={{
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
                    resetUpdateResult={jest.fn()}
                    downloadResult={{ status: "NOT_STARTED" }}
                    resetDownloadResult={jest.fn()}
                    deleteVersionResult={{ status: "NOT_STARTED" }}
                    resetDeleteVersionResult={jest.fn()}
                    resetAll={jest.fn()}
                />
            )

            const errorMessage = screen.getByTestId("ALERT_BOX_MOCK")
            expect(errorMessage).toHaveTextContent(
                "TITLE: file-view:directory-content-list.file-content-not-changed-error"
            )
        })
    })
})
