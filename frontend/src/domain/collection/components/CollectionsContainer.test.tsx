import { render, screen } from "@testing-library/react"
import React from "react"
import { exampleCollection, exampleCollectionId } from "../../../testutils/constants/collection"
import { tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { historyMock } from "../../../testutils/mocks/router-mock"
import { CollectionsContainer } from "./CollectionsContainer"

// eslint-disable-next-line react/display-name
jest.mock("./AutoCreateCollectionModal", () => () => <div data-testid="create-collection-modal"></div>)

jest.mock("react-router", () => ({
    ...jest.requireActual("react-router"),
    // eslint-disable-next-line react/display-name, @typescript-eslint/no-explicit-any
    Redirect: (props: any) => <div data-testid="redirect-component">{props.to}</div>,
}))

// eslint-disable-next-line react/display-name, @typescript-eslint/no-explicit-any
jest.mock("./SelectCollectionModal", () => (props: any) => (
    <div data-testid="select-collection-modal">{JSON.stringify(props.availableCollections)}</div>
))

describe("Collections containers", () => {
    it("should render create collection modal if collections list is empty", () => {
        render(
            <CollectionsContainer
                collections={{ status: "FINISHED", params: true, data: [] }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: null }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock()}
                fetchCollections={jest.fn()}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                cleanCollectionsData={jest.fn()}
            />
        )
        const modal = screen.getByTestId("create-collection-modal")
        expect(modal).toBeInTheDocument()
    })

    it("should redirect to collection if only one collection exists", () => {
        render(
            <CollectionsContainer
                collections={{ status: "FINISHED", params: true, data: [exampleCollection] }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: null }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock()}
                fetchCollections={jest.fn()}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                cleanCollectionsData={jest.fn()}
            />
        )
        const redirectComponent = screen.getByTestId("redirect-component")
        expect(redirectComponent.textContent).toEqual(`/collection/${exampleCollectionId}`)
    })

    it("should render select collection modal if collections more than one collection exists", () => {
        const collection1 = exampleCollection
        const collection2 = { ...exampleCollection, id: "a7a478e8-e264-4594-b237-3ee854cf210d" }
        render(
            <CollectionsContainer
                collections={{ status: "FINISHED", params: true, data: [collection1, collection2] }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: null }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock()}
                fetchCollections={jest.fn()}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                cleanCollectionsData={jest.fn()}
            />
        )
        const modal = screen.getByTestId("select-collection-modal")
        expect(modal.textContent).toEqual(JSON.stringify([collection1, collection2]))
    })

    it("should render error message if collections fetch failed", () => {
        render(
            <CollectionsContainer
                collections={{ status: "FAILED", params: true, error: new Error("Some error") }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: null }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock()}
                fetchCollections={jest.fn()}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                cleanCollectionsData={jest.fn()}
            />
        )
        const errorMessage = screen.getByText("collections-view:load-error-title")
        expect(errorMessage).toBeInTheDocument()
    })

    it("should render loader if collection fetch is in progress", () => {
        render(
            <CollectionsContainer
                collections={{ status: "PENDING", params: true }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: null }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock()}
                fetchCollections={jest.fn()}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                cleanCollectionsData={jest.fn()}
            />
        )
        const loader = screen.getByText("collections-view:loader-title")
        expect(loader).toBeInTheDocument()
    })

    it("should read preferred collection on mount if operation status is NOT_STARTED", () => {
        const getPreferredCollectionMock = jest.fn()

        render(
            <CollectionsContainer
                collections={{ status: "FINISHED", params: true, data: [] }}
                preferredCollection={{ status: "NOT_STARTED" }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock()}
                fetchCollections={jest.fn()}
                getPreferredCollection={getPreferredCollectionMock}
                removePreferredCollection={jest.fn()}
                cleanCollectionsData={jest.fn()}
            />
        )
        expect(getPreferredCollectionMock).toHaveBeenCalledTimes(1)
    })

    it("should read preferred collection on mount if operation status is FAILED", () => {
        const getPreferredCollectionMock = jest.fn()

        render(
            <CollectionsContainer
                collections={{ status: "FINISHED", params: true, data: [] }}
                preferredCollection={{ status: "FAILED", params: void 0, error: new Error("Some error") }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock()}
                fetchCollections={jest.fn()}
                getPreferredCollection={getPreferredCollectionMock}
                removePreferredCollection={jest.fn()}
                cleanCollectionsData={jest.fn()}
            />
        )
        expect(getPreferredCollectionMock).toHaveBeenCalledTimes(1)
    })

    it("should not read preferred collection on mount if operation status is FINISHED", () => {
        const getPreferredCollectionMock = jest.fn()

        render(
            <CollectionsContainer
                collections={{ status: "FINISHED", params: true, data: [] }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: null }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock()}
                fetchCollections={jest.fn()}
                getPreferredCollection={getPreferredCollectionMock}
                removePreferredCollection={jest.fn()}
                cleanCollectionsData={jest.fn()}
            />
        )
        expect(getPreferredCollectionMock).not.toHaveBeenCalled()
    })

    it("should not read preferred collection on mount if operation status is PENDING", () => {
        const getPreferredCollectionMock = jest.fn()

        render(
            <CollectionsContainer
                collections={{ status: "FINISHED", params: true, data: [] }}
                preferredCollection={{ status: "PENDING", params: void 0 }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock()}
                fetchCollections={jest.fn()}
                getPreferredCollection={getPreferredCollectionMock}
                removePreferredCollection={jest.fn()}
                cleanCollectionsData={jest.fn()}
            />
        )
        expect(getPreferredCollectionMock).not.toHaveBeenCalled()
    })

    it("should clean collections on unmount", () => {
        const cleanCollectionsMock = jest.fn()

        const element = render(
            <CollectionsContainer
                collections={{ status: "FINISHED", params: true, data: [] }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: null }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock()}
                fetchCollections={jest.fn()}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                cleanCollectionsData={cleanCollectionsMock}
            />
        )
        element.unmount()
        expect(cleanCollectionsMock).toHaveBeenCalledTimes(1)
    })

    it("should redirect to preferred collection", () => {
        const historyPushMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const fetchCollectionsMock = jest.fn()
        render(
            <CollectionsContainer
                collections={{ status: "NOT_STARTED" }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: exampleCollectionId }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock({ push: historyPushMock })}
                fetchCollections={fetchCollectionsMock}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
                cleanCollectionsData={jest.fn()}
            />
        )
        expect(historyPushMock).toHaveBeenCalledTimes(1)
        expect(historyPushMock).toHaveBeenCalledWith(`/collection/${exampleCollectionId}`)
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(fetchCollectionsMock).not.toHaveBeenCalled()
    })

    it("should remove preferred collection and not redirect if collection id is invalid", () => {
        const historyPushMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const fetchCollectionsMock = jest.fn()
        render(
            <CollectionsContainer
                collections={{ status: "NOT_STARTED" }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: "fooBar" }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock({ push: historyPushMock })}
                fetchCollections={fetchCollectionsMock}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
                cleanCollectionsData={jest.fn()}
            />
        )
        expect(historyPushMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).toHaveBeenCalledTimes(1)
        expect(fetchCollectionsMock).not.toHaveBeenCalled()
    })

    it("should fetch collections and not redirect if preferred collection is not set and collection result is NOT_STARTED", () => {
        const historyPushMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const fetchCollectionsMock = jest.fn()
        render(
            <CollectionsContainer
                collections={{ status: "NOT_STARTED" }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: null }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock({ push: historyPushMock })}
                fetchCollections={fetchCollectionsMock}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
                cleanCollectionsData={jest.fn()}
            />
        )
        expect(historyPushMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(fetchCollectionsMock).toHaveBeenCalledTimes(1)
    })

    it("should do nothing if preferred collection is not set and collection result is PENDING", () => {
        const historyPushMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const fetchCollectionsMock = jest.fn()
        render(
            <CollectionsContainer
                collections={{ status: "PENDING", params: true }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: null }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock({ push: historyPushMock })}
                fetchCollections={fetchCollectionsMock}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
                cleanCollectionsData={jest.fn()}
            />
        )
        expect(historyPushMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(fetchCollectionsMock).not.toHaveBeenCalled()
    })

    it("should do nothing if preferred collection is not set and collection result is FAILED", () => {
        const historyPushMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const fetchCollectionsMock = jest.fn()
        render(
            <CollectionsContainer
                collections={{ status: "FAILED", params: true, error: new Error("Some error") }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: null }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock({ push: historyPushMock })}
                fetchCollections={fetchCollectionsMock}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
                cleanCollectionsData={jest.fn()}
            />
        )
        expect(historyPushMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(fetchCollectionsMock).not.toHaveBeenCalled()
    })

    it("should do nothing if preferred collection is not set and collection result is FINISHED", () => {
        const historyPushMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const fetchCollectionsMock = jest.fn()
        render(
            <CollectionsContainer
                collections={{ status: "FINISHED", params: true, data: [] }}
                preferredCollection={{ status: "FINISHED", params: void 0, data: null }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock({ push: historyPushMock })}
                fetchCollections={fetchCollectionsMock}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
                cleanCollectionsData={jest.fn()}
            />
        )
        expect(historyPushMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(fetchCollectionsMock).not.toHaveBeenCalled()
    })

    it("should fetch collections and not redirect if preferred collection get failed and collection result is NOT_STARTED", () => {
        const historyPushMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const fetchCollectionsMock = jest.fn()
        render(
            <CollectionsContainer
                collections={{ status: "NOT_STARTED" }}
                preferredCollection={{ status: "FAILED", params: void 0, error: new Error("Some error") }}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                t={tFunctionMock}
                history={historyMock({ push: historyPushMock })}
                fetchCollections={fetchCollectionsMock}
                getPreferredCollection={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
                cleanCollectionsData={jest.fn()}
            />
        )
        expect(historyPushMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(fetchCollectionsMock).toHaveBeenCalledTimes(1)
    })

    it("should get preferred collection if remove finished", () => {
        const getPreferredCollectionMock = jest.fn()
        render(
            <CollectionsContainer
                collections={{ status: "NOT_STARTED" }}
                preferredCollection={{ status: "FAILED", params: void 0, error: new Error("Some error") }}
                removePreferredCollectionResult={{ status: "FINISHED", params: undefined, data: undefined }}
                t={tFunctionMock}
                history={historyMock()}
                fetchCollections={jest.fn()}
                getPreferredCollection={getPreferredCollectionMock}
                removePreferredCollection={jest.fn()}
                cleanCollectionsData={jest.fn()}
            />
        )

        expect(getPreferredCollectionMock).toHaveBeenCalledTimes(2)
    })
})
