import React from "react"
import { fireEvent, screen } from "@testing-library/react"
import { exampleCollection, exampleCollectionId } from "../../../testutils/constants/collection"
import { CollectionFilesContainer } from "./CollectionFilesContainer"
import {
    exampleChildDirectory2,
    exampleDirectoryData,
    exampleDirectoryTree,
    exampleFileData,
    exampleFileId,
} from "../../../testutils/constants/files"
import { tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { FileNotFound } from "../api/errors"
import { renderWithRoute } from "../../../testutils/helpers"
import { exampleUserId, exampleUserNickname } from "../../../testutils/constants/user"

jest.mock("../../../application/components/common/UnexpectedErrorMessage")

jest.mock("../../../application/components/common/EmptyPlaceholder")

jest.mock("../../../application/components/common/Loader")

jest.mock("./metadata_view/SingleObjectView")

const startRoute = `/collection/${exampleCollectionId}/file/${exampleFileId}`
const routeTemplate = "/collection/:collectionId/file/:fileId"

const renderWithPath = renderWithRoute(routeTemplate)

const startRouteRoot = `/collection/${exampleCollectionId}`
const routeTemplateRoot = "/collection/:collectionId"

const renderWithPathForRoot = renderWithRoute(routeTemplateRoot)

describe("Collection files container", () => {
    beforeEach(() => window.history.replaceState({}, "", startRoute))

    it("should render unexpected error message", () => {
        renderWithPath(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{
                    status: "FAILED",
                    params: { collectionId: exampleCollectionId, objectId: null },
                    error: new Error("Some error"),
                }}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{}}
                fetchAuthors={jest.fn()}
            />
        )
        const errorBox = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
        expect(errorBox).toHaveTextContent("Some error")
    })

    it("should render file not found placeholder", () => {
        renderWithPath(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{
                    status: "FAILED",
                    params: { collectionId: exampleCollectionId, objectId: null },
                    error: new FileNotFound(exampleFileId, exampleCollectionId),
                }}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{}}
                fetchAuthors={jest.fn()}
            />
        )
        const emptyPlaceholder = screen.getByTestId("EMPTY_PLACEHOLDER_MOCK")
        expect(emptyPlaceholder).toHaveTextContent("file-view:file-not-found-error")
    })

    it("should render loader if fetch status is NOT_STARTED", () => {
        renderWithPath(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{ status: "NOT_STARTED" }}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{}}
                fetchAuthors={jest.fn()}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader).toHaveTextContent("file-view:loader-title")
    })

    it("should render loader if fetch status is PENDING", () => {
        renderWithPath(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{ status: "PENDING", params: { collectionId: exampleCollectionId, objectId: null } }}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{}}
                fetchAuthors={jest.fn()}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader).toHaveTextContent("file-view:loader-title")
    })

    it("should render object view", () => {
        renderWithPath(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, objectId: null },
                    data: exampleDirectoryTree,
                }}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{}}
                fetchAuthors={jest.fn()}
            />
        )
        const objectView = screen.getByTestId("OBJECT_VIEW_MOCK")
        expect(objectView).toHaveTextContent(`Collection: ${exampleCollectionId}`)
        expect(objectView).toHaveTextContent(`Tree: ${JSON.stringify(exampleDirectoryTree)}`)
    })

    it("should fetch tree on mount", () => {
        const fetchTreeMock = jest.fn()

        renderWithPath(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{ status: "NOT_STARTED" }}
                fetchTree={fetchTreeMock}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{}}
                fetchAuthors={jest.fn()}
            />
        )

        expect(fetchTreeMock).toHaveBeenCalledTimes(1)
        expect(fetchTreeMock).toHaveBeenCalledWith(exampleCollectionId, exampleFileId)
    })

    it("should fetch tree with null file ID on mount", () => {
        window.history.replaceState({}, "", startRouteRoot)
        const fetchTreeMock = jest.fn()

        renderWithPathForRoot(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{ status: "NOT_STARTED" }}
                fetchTree={fetchTreeMock}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{}}
                fetchAuthors={jest.fn()}
            />
        )

        expect(fetchTreeMock).toHaveBeenCalledTimes(1)
        expect(fetchTreeMock).toHaveBeenCalledWith(exampleCollectionId, null)
    })

    it("should reset tree on unmount", () => {
        const resetTreeMock = jest.fn()

        const view = renderWithPath(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{ status: "NOT_STARTED" }}
                fetchTree={jest.fn()}
                resetTree={resetTreeMock}
                t={tFunctionMock}
                knownUsers={{}}
                fetchAuthors={jest.fn()}
            />
        )

        view.unmount()

        expect(resetTreeMock).toHaveBeenCalledTimes(1)
    })

    it("should fetch tree on navigate to another file", () => {
        const fetchTreeMock = jest.fn()

        renderWithPath(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, objectId: null },
                    data: exampleDirectoryTree,
                }}
                fetchTree={fetchTreeMock}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{}}
                fetchAuthors={jest.fn()}
            />
        )

        const childLink = screen.getByText(`Go to ${exampleChildDirectory2.name}`)
        fireEvent.click(childLink)

        expect(fetchTreeMock).toHaveBeenCalledTimes(2)
        expect(fetchTreeMock).toHaveBeenNthCalledWith(1, exampleCollectionId, exampleFileId)
        expect(fetchTreeMock).toHaveBeenNthCalledWith(2, exampleCollectionId, exampleChildDirectory2.id)
    })

    it("should fetch missing authors", () => {
        window.history.replaceState({}, "", startRouteRoot)
        const fetchAuthorsMock = jest.fn()

        renderWithPathForRoot(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, objectId: null },
                    data: {
                        children: [
                            exampleFileData,
                            exampleDirectoryData,
                            { ...exampleFileData, id: "f1", name: "file1", versionId: "v1.1", versionAuthor: null },
                            { ...exampleFileData, id: "f2", name: "file2", versionId: "v2.1", versionAuthor: "foo" },
                            { ...exampleDirectoryData, id: "d1", name: "dir1" },
                            { ...exampleFileData, id: "f3", name: "file3", versionId: "v3.1", versionAuthor: "bar" },
                            { ...exampleFileData, id: "f4", name: "file4", versionId: "v4.1", versionAuthor: "baz" },
                            { ...exampleFileData, id: "f5", name: "file5", versionId: "v5.1", versionAuthor: "qux" },
                        ],
                    },
                }}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{
                    foo: { status: "FINISHED", params: "foo", data: { userId: "foo", nickName: "n1" } },
                    baz: { status: "FINISHED", params: "baz", data: { userId: "baz", nickName: "n2" } },
                }}
                fetchAuthors={fetchAuthorsMock}
            />
        )
        expect(fetchAuthorsMock).toHaveBeenCalledTimes(1)
        expect(fetchAuthorsMock).toHaveBeenCalledWith([exampleFileData.versionAuthor, "bar", "qux"])
    })

    it("should not fetch authors if tree is not fetched yet", () => {
        window.history.replaceState({}, "", startRouteRoot)
        const fetchAuthorsMock = jest.fn()

        renderWithPathForRoot(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{
                    status: "PENDING",
                    params: { collectionId: exampleCollectionId, objectId: null },
                }}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{
                    foo: { status: "FINISHED", params: "foo", data: { userId: "foo", nickName: "n1" } },
                    baz: { status: "FINISHED", params: "baz", data: { userId: "baz", nickName: "n2" } },
                }}
                fetchAuthors={fetchAuthorsMock}
            />
        )
        expect(fetchAuthorsMock).not.toHaveBeenCalled()
    })

    it("should not fetch authors if fetched file tree", () => {
        window.history.replaceState({}, "", startRouteRoot)
        const fetchAuthorsMock = jest.fn()

        renderWithPathForRoot(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, objectId: null },
                    data: {
                        metadata: exampleFileData,
                        parents: [exampleDirectoryData],
                    },
                }}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{
                    foo: { status: "FINISHED", params: "foo", data: { userId: "foo", nickName: "n1" } },
                    baz: { status: "FINISHED", params: "baz", data: { userId: "baz", nickName: "n2" } },
                }}
                fetchAuthors={fetchAuthorsMock}
            />
        )
        expect(fetchAuthorsMock).not.toHaveBeenCalled()
    })

    it("should not fetch authors if all users are known", () => {
        window.history.replaceState({}, "", startRouteRoot)
        const fetchAuthorsMock = jest.fn()

        renderWithPathForRoot(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, objectId: null },
                    data: {
                        children: [
                            exampleFileData,
                            exampleDirectoryData,
                            { ...exampleFileData, id: "f1", name: "file1", versionId: "v1.1", versionAuthor: null },
                            { ...exampleFileData, id: "f2", name: "file2", versionId: "v2.1", versionAuthor: "foo" },
                            { ...exampleDirectoryData, id: "d1", name: "dir1" },
                            { ...exampleFileData, id: "f3", name: "file3", versionId: "v3.1", versionAuthor: "bar" },
                            { ...exampleFileData, id: "f4", name: "file4", versionId: "v4.1", versionAuthor: "baz" },
                            { ...exampleFileData, id: "f5", name: "file5", versionId: "v5.1", versionAuthor: "qux" },
                        ],
                    },
                }}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
                knownUsers={{
                    [exampleUserId]: {
                        status: "FINISHED",
                        params: exampleUserId,
                        data: { userId: exampleUserId, nickName: exampleUserNickname },
                    },
                    foo: { status: "FINISHED", params: "foo", data: { userId: "foo", nickName: "n1" } },
                    baz: { status: "FINISHED", params: "baz", data: { userId: "baz", nickName: "n2" } },
                    bar: { status: "FINISHED", params: "bar", data: { userId: "bar", nickName: "n3" } },
                    qux: { status: "FINISHED", params: "qux", data: { userId: "qux", nickName: "n4" } },
                }}
                fetchAuthors={fetchAuthorsMock}
            />
        )
        expect(fetchAuthorsMock).not.toHaveBeenCalled()
    })
})
