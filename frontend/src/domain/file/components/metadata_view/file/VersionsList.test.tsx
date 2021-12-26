import { fireEvent, render, screen } from "@testing-library/react"
import React from "react"
import { exampleCollectionId, exampleEncryptionKey } from "../../../../../testutils/constants/collection"
import { exampleFileData, exampleVersion } from "../../../../../testutils/constants/files"
import { examplePrivateKey, exampleUserId } from "../../../../../testutils/constants/user"
import { i18nMock, tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { DELETE_VERSION_BUTTON, DOWNLOAD_VERSION_BUTTON } from "../../testids"
import { VersionsList } from "./VersionsList"

// eslint-disable-next-line react/display-name
jest.mock("./DeleteVersionModal", () => () => <div data-testid="DELETE_VERSION_MODAL"></div>)

// eslint-disable-next-line react/display-name
jest.mock("./Author", () => () => <div data-testid="AUTHOR_MOCK"></div>)

jest.mock("../../../../../application/components/common/UnexpectedErrorMessage")

jest.mock("../../../../../application/components/common/LoadingOverlay")

jest.mock("../../../../../application/components/common/Loader")

const knownUsers = {}

describe("Versions list", () => {
    describe("mount", () => {
        it("should not fetch versions if status is FINISHED", () => {
            const fetchVersionsMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id },
                        data: [exampleVersion],
                    }}
                    fetchVersions={fetchVersionsMock}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            expect(fetchVersionsMock).not.toHaveBeenCalled()
        })

        it("should not fetch versions if status is PENDING", () => {
            const fetchVersionsMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "PENDING",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id },
                    }}
                    fetchVersions={fetchVersionsMock}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            expect(fetchVersionsMock).not.toHaveBeenCalled()
        })

        it("should fetch versions if status is FAILED", () => {
            const fetchVersionsMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FAILED",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id },
                        error: new Error("Some error"),
                    }}
                    fetchVersions={fetchVersionsMock}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            expect(fetchVersionsMock).toHaveBeenCalledTimes(1)
            expect(fetchVersionsMock).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
        })

        it("should fetch versions if status is NOT_STARTED", () => {
            const fetchVersionsMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{ status: "NOT_STARTED" }}
                    fetchVersions={fetchVersionsMock}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            expect(fetchVersionsMock).toHaveBeenCalledTimes(1)
            expect(fetchVersionsMock).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
        })

        it("should fetch versions if collection ID does not match", () => {
            const fetchVersionsMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: "other-collection", fileId: exampleFileData.id },
                        data: [exampleVersion],
                    }}
                    fetchVersions={fetchVersionsMock}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            expect(fetchVersionsMock).toHaveBeenCalledTimes(1)
            expect(fetchVersionsMock).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
        })

        it("should fetch versions if file ID does not match", () => {
            const fetchVersionsMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileId: "other-file" },
                        data: [exampleVersion],
                    }}
                    fetchVersions={fetchVersionsMock}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            expect(fetchVersionsMock).toHaveBeenCalledTimes(1)
            expect(fetchVersionsMock).toHaveBeenCalledWith(exampleCollectionId, exampleFileData.id)
        })

        it("should fetch missing authors data", () => {
            const fetchAuthorsMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileId: "other-file" },
                        data: [
                            exampleVersion,
                            { ...exampleVersion, versionId: "1", versionAuthor: "foo" },
                            { ...exampleVersion, versionId: "2", versionAuthor: "bar" },
                            { ...exampleVersion, versionId: "3", versionAuthor: "baz" },
                            { ...exampleVersion, versionId: "4", versionAuthor: "qux" },
                        ],
                    }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={{
                        foo: { status: "FINISHED", params: "foo", data: { userId: "foo", nickName: "n1" } },
                        baz: { status: "FINISHED", params: "baz", data: { userId: "baz", nickName: "n2" } },
                    }}
                    fetchAuthorsData={fetchAuthorsMock}
                />
            )
            expect(fetchAuthorsMock).toHaveBeenCalledTimes(1)
            expect(fetchAuthorsMock).toHaveBeenCalledWith([exampleVersion.versionAuthor, "bar", "qux"])
        })

        it("should not fetch authors data if version not fetched yet", () => {
            const fetchAuthorsMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "PENDING",
                        params: { collectionId: exampleCollectionId, fileId: "other-file" },
                    }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={{
                        foo: { status: "FINISHED", params: "foo", data: { userId: "foo", nickName: "n1" } },
                        baz: { status: "FINISHED", params: "baz", data: { userId: "baz", nickName: "n2" } },
                    }}
                    fetchAuthorsData={fetchAuthorsMock}
                />
            )
            expect(fetchAuthorsMock).not.toHaveBeenCalled()
        })

        it("should not fetch authors data if all data fetched before", () => {
            const fetchAuthorsMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileId: "other-file" },
                        data: [
                            exampleVersion,
                            { ...exampleVersion, versionId: "1", versionAuthor: "foo" },
                            { ...exampleVersion, versionId: "2", versionAuthor: "bar" },
                            { ...exampleVersion, versionId: "3", versionAuthor: "baz" },
                            { ...exampleVersion, versionId: "4", versionAuthor: "qux" },
                        ],
                    }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={{
                        [exampleUserId]: {
                            status: "FINISHED",
                            params: exampleUserId,
                            data: { userId: exampleUserId, nickName: "n0" },
                        },
                        foo: { status: "FINISHED", params: "foo", data: { userId: "foo", nickName: "n1" } },
                        bar: { status: "FINISHED", params: "bar", data: { userId: "bar", nickName: "n3" } },
                        baz: { status: "FINISHED", params: "baz", data: { userId: "baz", nickName: "n2" } },
                        qux: { status: "FINISHED", params: "qux", data: { userId: "qux", nickName: "n4" } },
                    }}
                    fetchAuthorsData={fetchAuthorsMock}
                />
            )
            expect(fetchAuthorsMock).not.toHaveBeenCalled()
        })

        it("should not fetch authors data if no version found", () => {
            const fetchAuthorsMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileId: "other-file" },
                        data: [],
                    }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={{
                        foo: { status: "FINISHED", params: "foo", data: { userId: "foo", nickName: "n1" } },
                        baz: { status: "FINISHED", params: "baz", data: { userId: "baz", nickName: "n2" } },
                    }}
                    fetchAuthorsData={fetchAuthorsMock}
                />
            )
            expect(fetchAuthorsMock).not.toHaveBeenCalled()
        })
    })

    describe("loader", () => {
        it("should be visible if status is NOT_STARTED", () => {
            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{ status: "NOT_STARTED" }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            const loader = screen.getByTestId("LOADER_MOCK")
            expect(loader).toHaveTextContent("file-view:file-data.loading-versions")
        })

        it("should be visible if status is PENDING", () => {
            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "PENDING",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id },
                    }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            const loader = screen.getByTestId("LOADER_MOCK")
            expect(loader).toHaveTextContent("file-view:file-data.loading-versions")
        })
    })

    it("should render unexpected error message on fetch failure", () => {
        render(
            <VersionsList
                collectionId={exampleCollectionId}
                fileId={exampleFileData.id}
                t={tFunctionMock}
                i18n={i18nMock()}
                versionsResult={{
                    status: "FAILED",
                    params: { collectionId: exampleCollectionId, fileId: exampleFileData.id },
                    error: new Error("Some error"),
                }}
                fetchVersions={jest.fn()}
                encryptionKey={{ status: "NOT_STARTED" }}
                downloadVersion={jest.fn()}
                requestVersionDelete={jest.fn()}
                deleteResult={{ status: "NOT_STARTED" }}
                knownUsers={knownUsers}
                fetchAuthorsData={jest.fn()}
            />
        )

        const errorMessage = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
        expect(errorMessage).toHaveTextContent("Some error")
    })

    describe("loading overlay", () => {
        it("should be visible if delete status is PENDING", () => {
            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id },
                        data: [exampleVersion],
                    }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{
                        status: "PENDING",
                        params: {
                            collectionId: exampleCollectionId,
                            fileId: exampleFileData.id,
                            versionId: exampleVersion.versionId,
                        },
                    }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            const loader = screen.getByTestId("LOADING_OVERLAY_MOCK")
            expect(loader).toHaveTextContent("Common:action-in-progress")
            expect(loader).toHaveAttribute("aria-hidden", "false")
        })

        it("should be hidden", () => {
            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id },
                        data: [exampleVersion],
                    }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            const loader = screen.getByTestId("LOADING_OVERLAY_MOCK")
            expect(loader).toHaveAttribute("aria-hidden", "true")
        })
    })

    describe("download version button", () => {
        it("should be disabled if encryption key is locked", () => {
            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id },
                        data: [exampleVersion, { ...exampleVersion, versionId: "other-version" }],
                    }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            const button = screen.getByTestId(`${DOWNLOAD_VERSION_BUTTON}_other-version`)
            expect(button).toBeDisabled()
        })

        it("should be enabled if encryption key is unlocked", () => {
            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id },
                        data: [exampleVersion, { ...exampleVersion, versionId: "other-version" }],
                    }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                        data: exampleEncryptionKey,
                    }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            const button = screen.getByTestId(`${DOWNLOAD_VERSION_BUTTON}_other-version`)
            expect(button).toBeEnabled()
        })

        it("should trigger action on click", () => {
            const downloadMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id },
                        data: [exampleVersion, { ...exampleVersion, versionId: "other-version" }],
                    }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
                        data: exampleEncryptionKey,
                    }}
                    downloadVersion={downloadMock}
                    requestVersionDelete={jest.fn()}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            const button = screen.getByTestId(`${DOWNLOAD_VERSION_BUTTON}_other-version`)
            fireEvent.click(button)

            expect(downloadMock).toHaveBeenCalledTimes(1)
            expect(downloadMock).toHaveBeenCalledWith(
                exampleCollectionId,
                exampleFileData.id,
                "other-version",
                exampleEncryptionKey
            )
        })
    })

    describe("delete button", () => {
        it("should trigger action on click", () => {
            const deleteMock = jest.fn()

            render(
                <VersionsList
                    collectionId={exampleCollectionId}
                    fileId={exampleFileData.id}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    versionsResult={{
                        status: "FINISHED",
                        params: { collectionId: exampleCollectionId, fileId: exampleFileData.id },
                        data: [exampleVersion, { ...exampleVersion, versionId: "other-version" }],
                    }}
                    fetchVersions={jest.fn()}
                    encryptionKey={{ status: "NOT_STARTED" }}
                    downloadVersion={jest.fn()}
                    requestVersionDelete={deleteMock}
                    deleteResult={{ status: "NOT_STARTED" }}
                    knownUsers={knownUsers}
                    fetchAuthorsData={jest.fn()}
                />
            )

            const button = screen.getByTestId(`${DELETE_VERSION_BUTTON}_other-version`)

            fireEvent.click(button)

            expect(deleteMock).toHaveBeenCalledTimes(1)
            expect(deleteMock).toHaveBeenCalledWith({ ...exampleVersion, versionId: "other-version" })
        })
    })
})
