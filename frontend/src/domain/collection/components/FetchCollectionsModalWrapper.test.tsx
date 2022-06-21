import { render, screen } from "@testing-library/react"
import React from "react"
import { exampleCollection, exampleCollectionId } from "../../../testutils/constants/collection"
import { tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { Collection } from "../types/Collection"
import { FetchCollectionsModalWrapper } from "./FetchCollectionsModalWrapper"

jest.mock("../../../application/components/common/Loader")

jest.mock("../../../application/components/common/AlertBox")

jest.mock(
    "./SelectCollectionModal",
    // eslint-disable-next-line react/display-name
    () => (props: { availableCollections: Collection[]; selectedCollection?: string }) =>
        (
            <div data-testid="SELECT_COLLECTION_MODAL_MOCK">
                <div>SELECTED_COLLECTION: {props.selectedCollection}</div>
                <div>COLLECTIONS: {JSON.stringify(props.availableCollections)}</div>
            </div>
        )
)

describe("Fetch collections modal wrapper", () => {
    it("should render loader if collections list is loading", () => {
        render(
            <FetchCollectionsModalWrapper
                isOpen={true}
                onClose={jest.fn()}
                collectionsResult={{ status: "PENDING", params: true }}
                fetchCollections={jest.fn()}
                activeCollection={exampleCollectionId}
                t={tFunctionMock}
                resetCollections={jest.fn()}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader.textContent).toBe("collections-view:loader-title")
    })

    it("should show error message on fetch failure", () => {
        render(
            <FetchCollectionsModalWrapper
                isOpen={true}
                onClose={jest.fn()}
                collectionsResult={{ status: "FAILED", params: true, error: new Error("Some error") }}
                fetchCollections={jest.fn()}
                activeCollection={exampleCollectionId}
                t={tFunctionMock}
                resetCollections={jest.fn()}
            />
        )
        const alertBox = screen.getByTestId("ALERT_BOX_MOCK")
        expect(alertBox.textContent).toMatch("TITLE: collections-view:load-error-title")
    })

    it("should render collection list to select", () => {
        render(
            <FetchCollectionsModalWrapper
                isOpen={true}
                onClose={jest.fn()}
                collectionsResult={{ status: "FINISHED", params: true, data: [exampleCollection] }}
                fetchCollections={jest.fn()}
                activeCollection={exampleCollectionId}
                t={tFunctionMock}
                resetCollections={jest.fn()}
            />
        )
        const modal = screen.getByTestId("SELECT_COLLECTION_MODAL_MOCK")
        expect(modal.textContent).toMatch(`SELECTED_COLLECTION: ${exampleCollectionId}`)
        expect(modal.textContent).toMatch(`COLLECTIONS: ${JSON.stringify([exampleCollection])}`)
    })

    it("should filter out non accepted collections", () => {
        const collection1 = exampleCollection
        const collection2 = { ...exampleCollection, id: "c2", isAccepted: false }
        const collection3 = { ...exampleCollection, id: "c3" }
        const collection4 = { ...exampleCollection, id: "c4", isAccepted: false }

        render(
            <FetchCollectionsModalWrapper
                isOpen={true}
                onClose={jest.fn()}
                collectionsResult={{
                    status: "FINISHED",
                    params: true,
                    data: [collection1, collection2, collection3, collection4],
                }}
                fetchCollections={jest.fn()}
                activeCollection={exampleCollectionId}
                t={tFunctionMock}
                resetCollections={jest.fn()}
            />
        )
        const modal = screen.getByTestId("SELECT_COLLECTION_MODAL_MOCK")

        expect(modal.textContent).toMatch(`SELECTED_COLLECTION: ${exampleCollectionId}`)
        expect(modal.textContent).toMatch(`COLLECTIONS: ${JSON.stringify([collection1, collection3])}`)
    })

    it("should render nothing if isOpen is false", () => {
        const { container } = render(
            <FetchCollectionsModalWrapper
                isOpen={false}
                onClose={jest.fn()}
                collectionsResult={{ status: "FINISHED", params: true, data: [exampleCollection] }}
                fetchCollections={jest.fn()}
                activeCollection={exampleCollectionId}
                t={tFunctionMock}
                resetCollections={jest.fn()}
            />
        )
        expect(container).toBeEmptyDOMElement()
    })

    it("should fetch collections if status is NOT_STARTED", () => {
        const fetchCollectionsMock = jest.fn()

        render(
            <FetchCollectionsModalWrapper
                isOpen={true}
                onClose={jest.fn()}
                collectionsResult={{ status: "NOT_STARTED" }}
                fetchCollections={fetchCollectionsMock}
                activeCollection={exampleCollectionId}
                t={tFunctionMock}
                resetCollections={jest.fn()}
            />
        )

        expect(fetchCollectionsMock).toHaveBeenCalledTimes(1)
    })

    it("should fetch collections if status is FAILED", () => {
        const fetchCollectionsMock = jest.fn()

        render(
            <FetchCollectionsModalWrapper
                isOpen={true}
                onClose={jest.fn()}
                collectionsResult={{ status: "FAILED", params: true, error: new Error("Some error") }}
                fetchCollections={fetchCollectionsMock}
                activeCollection={exampleCollectionId}
                t={tFunctionMock}
                resetCollections={jest.fn()}
            />
        )

        expect(fetchCollectionsMock).toHaveBeenCalledTimes(1)
    })

    it("should not fetch collections if isOpen is false", () => {
        const fetchCollectionsMock = jest.fn()

        render(
            <FetchCollectionsModalWrapper
                isOpen={false}
                onClose={jest.fn()}
                collectionsResult={{ status: "NOT_STARTED" }}
                fetchCollections={fetchCollectionsMock}
                activeCollection={exampleCollectionId}
                t={tFunctionMock}
                resetCollections={jest.fn()}
            />
        )

        expect(fetchCollectionsMock).not.toHaveBeenCalled()
    })

    it("should not fetch collections if status is PENDING", () => {
        const fetchCollectionsMock = jest.fn()

        render(
            <FetchCollectionsModalWrapper
                isOpen={true}
                onClose={jest.fn()}
                collectionsResult={{ status: "PENDING", params: true }}
                fetchCollections={fetchCollectionsMock}
                activeCollection={exampleCollectionId}
                t={tFunctionMock}
                resetCollections={jest.fn()}
            />
        )

        expect(fetchCollectionsMock).not.toHaveBeenCalled()
    })

    it("should not fetch collections if status is FINISHED", () => {
        const fetchCollectionsMock = jest.fn()

        render(
            <FetchCollectionsModalWrapper
                isOpen={true}
                onClose={jest.fn()}
                collectionsResult={{ status: "FINISHED", params: true, data: [exampleCollection] }}
                fetchCollections={fetchCollectionsMock}
                activeCollection={exampleCollectionId}
                t={tFunctionMock}
                resetCollections={jest.fn()}
            />
        )

        expect(fetchCollectionsMock).not.toHaveBeenCalled()
    })
})
