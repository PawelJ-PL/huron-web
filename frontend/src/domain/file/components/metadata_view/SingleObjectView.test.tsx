import { render, screen } from "@testing-library/react"
import React from "react"
import { exampleCollection, exampleCollectionId } from "../../../../testutils/constants/collection"
import { exampleDirectoryTree, exampleFileTree } from "../../../../testutils/constants/files"
import { FileMetadata, FilesystemUnitMetadata } from "../../types/FilesystemUnitMetadata"
import SingleObjectView from "./SingleObjectView"

// eslint-disable-next-line react/display-name
jest.mock("./FileBreadcrumb", () => () => <div></div>)

// eslint-disable-next-line react/display-name
jest.mock("./file/FileView", () => (props: { metadata: FileMetadata }) => (
    <div data-testid="FILE_VIEW_MOCK">{JSON.stringify(props.metadata)}</div>
))

jest.mock(
    "./directory/DirectoryView",
    // eslint-disable-next-line react/display-name
    () => (props: { childObjects: FilesystemUnitMetadata[]; collectionId: string; thisDirectoryId: string | null }) =>
        (
            <div data-testid="DIRECTORY_VIEW_MOCK">
                <div>Collection: {props.collectionId}</div>
                <div>Directory: {props.thisDirectoryId}</div>
                <div>Children: {JSON.stringify(props.childObjects)}</div>
            </div>
        )
)

describe("Single object view", () => {
    it("should render file view", () => {
        render(<SingleObjectView collection={exampleCollection} objectTree={exampleFileTree} />)
        const view = screen.getByTestId("FILE_VIEW_MOCK")
        expect(view).toHaveTextContent(JSON.stringify(exampleFileTree.metadata))
    })

    it("should render directory view", () => {
        render(<SingleObjectView collection={exampleCollection} objectTree={exampleDirectoryTree} />)
        const view = screen.getByTestId("DIRECTORY_VIEW_MOCK")
        expect(view).toHaveTextContent(`Collection: ${exampleCollectionId}`)
        expect(view).toHaveTextContent(`Directory: ${exampleDirectoryTree.metadata.id}`)
        expect(view).toHaveTextContent(`Children: ${JSON.stringify(exampleDirectoryTree.children)}`)
    })
})
