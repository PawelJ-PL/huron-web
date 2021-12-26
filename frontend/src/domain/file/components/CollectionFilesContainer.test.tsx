import React from "react"
import { fireEvent, screen } from "@testing-library/react"
import { exampleCollection, exampleCollectionId } from "../../../testutils/constants/collection"
import { CollectionFilesContainer } from "./CollectionFilesContainer"
import { exampleChildDirectory2, exampleDirectoryTree, exampleFileId } from "../../../testutils/constants/files"
import { tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { FileNotFound } from "../api/errors"
import { renderWithRoute } from "../../../testutils/helpers"

jest.mock("../../../application/components/common/UnexpectedErrorMessage")

jest.mock("../../../application/components/common/EmptyPlaceholder")

jest.mock("../../../application/components/common/Loader")

jest.mock("./metadata_view/SingleObjectView")

const startRoute = `/collection/${exampleCollectionId}/file/${exampleFileId}`
const routeTemplate = "/collection/:collectionId/file/:fileId"

// eslint-disable-next-line testing-library/render-result-naming-convention
const renderWithPath = renderWithRoute(routeTemplate)

const startRouteRoot = `/collection/${exampleCollectionId}`
const routeTemplateRoot = "/collection/:collectionId"

// eslint-disable-next-line testing-library/render-result-naming-convention
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
            />
        )

        const childLink = screen.getByText(`Go to ${exampleChildDirectory2.name}`)
        fireEvent.click(childLink)

        expect(fetchTreeMock).toHaveBeenCalledTimes(2)
        expect(fetchTreeMock).toHaveBeenNthCalledWith(1, exampleCollectionId, exampleFileId)
        expect(fetchTreeMock).toHaveBeenNthCalledWith(2, exampleCollectionId, exampleChildDirectory2.id)
    })
})
