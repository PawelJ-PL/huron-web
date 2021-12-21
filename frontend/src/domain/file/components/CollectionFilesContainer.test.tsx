import React from "react"
import { render, screen } from "@testing-library/react"
import { exampleCollection, exampleCollectionId } from "../../../testutils/constants/collection"
import { historyMock } from "../../../testutils/mocks/router-mock"
import { CollectionFilesContainer } from "./CollectionFilesContainer"
import * as H from "history"
import { match } from "react-router"
import { exampleDirectoryTree, exampleFileId } from "../../../testutils/constants/files"
import { tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { FileNotFound } from "../api/errors"
import { Collection } from "../../collection/types/Collection"
import { ObjectTree } from "../types/ObjectTree"

jest.mock("../../../application/components/common/UnexpectedErrorMessage")

jest.mock("../../../application/components/common/EmptyPlaceholder")

jest.mock("../../../application/components/common/Loader")

// eslint-disable-next-line react/display-name
jest.mock("./metadata_view/SingleObjectView", () => (props: { collection: Collection; objectTree: ObjectTree }) => (
    <div data-testid="OBJECT_VIEW_MOCK">
        <div>Collection: {props.collection.id}</div>
        <div>Tree: {JSON.stringify(props.objectTree)}</div>
    </div>
))

const exampleLocation: H.Location<unknown> = {
    pathname: "/collection/:collectionId/file/:fileId",
    search: "",
    state: {},
    hash: "",
}

const exampleMatch: match<{ fileId?: string }> = {
    isExact: true,
    path: exampleLocation.pathname,
    url: `/collection/${exampleCollectionId}/file/${exampleFileId}`,
    params: { fileId: exampleFileId },
}

describe("Collection files container", () => {
    it("should render unexpected error message", () => {
        render(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{
                    status: "FAILED",
                    params: { collectionId: exampleCollectionId, objectId: null },
                    error: new Error("Some error"),
                }}
                history={historyMock()}
                location={exampleLocation}
                match={exampleMatch}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
            />
        )
        const errorBox = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
        expect(errorBox).toHaveTextContent("Some error")
    })

    it("should render file not found placeholder", () => {
        render(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{
                    status: "FAILED",
                    params: { collectionId: exampleCollectionId, objectId: null },
                    error: new FileNotFound(exampleFileId, exampleCollectionId),
                }}
                history={historyMock()}
                location={exampleLocation}
                match={exampleMatch}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
            />
        )
        const emptyPlaceholder = screen.getByTestId("EMPTY_PLACEHOLDER_MOCK")
        expect(emptyPlaceholder).toHaveTextContent("file-view:file-not-found-error")
    })

    it("should render loader if fetch status is NOT_STARTED", () => {
        render(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{ status: "NOT_STARTED" }}
                history={historyMock()}
                location={exampleLocation}
                match={exampleMatch}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader).toHaveTextContent("file-view:loader-title")
    })

    it("should render loader if fetch status is PENDING", () => {
        render(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{ status: "PENDING", params: { collectionId: exampleCollectionId, objectId: null } }}
                history={historyMock()}
                location={exampleLocation}
                match={exampleMatch}
                fetchTree={jest.fn()}
                resetTree={jest.fn()}
                t={tFunctionMock}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader).toHaveTextContent("file-view:loader-title")
    })

    it("should render object view", () => {
        render(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, objectId: null },
                    data: exampleDirectoryTree,
                }}
                history={historyMock()}
                location={exampleLocation}
                match={exampleMatch}
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

        render(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{ status: "NOT_STARTED" }}
                history={historyMock()}
                location={exampleLocation}
                match={exampleMatch}
                fetchTree={fetchTreeMock}
                resetTree={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchTreeMock).toHaveBeenCalledTimes(1)
        expect(fetchTreeMock).toHaveBeenCalledWith(exampleCollectionId, exampleFileId)
    })

    it("should fetch tree with null file ID on mount", () => {
        const fetchTreeMock = jest.fn()

        const updatedMatch = { ...exampleMatch, params: { fileId: undefined } }

        render(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{ status: "NOT_STARTED" }}
                history={historyMock()}
                location={exampleLocation}
                match={updatedMatch}
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

        const view = render(
            <CollectionFilesContainer
                collection={exampleCollection}
                fileTreeResult={{ status: "NOT_STARTED" }}
                history={historyMock()}
                location={exampleLocation}
                match={exampleMatch}
                fetchTree={jest.fn()}
                resetTree={resetTreeMock}
                t={tFunctionMock}
            />
        )

        view.unmount()

        expect(resetTreeMock).toHaveBeenCalledTimes(1)
    })
})
