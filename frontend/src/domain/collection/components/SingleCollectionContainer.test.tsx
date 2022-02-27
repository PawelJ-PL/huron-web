/* eslint-disable jest/prefer-to-be */
import { screen } from "@testing-library/react"
import React from "react"
import { exampleCollection, exampleCollectionId } from "../../../testutils/constants/collection"
import { renderWithRoute } from "../../../testutils/helpers"
import { tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { Collection } from "../types/Collection"
import { SingleCollectionContainer } from "./SingleCollectionContainer"

jest.mock("../../../application/components/common/AlertBox")

jest.mock("../../../application/components/common/Loader")

// eslint-disable-next-line react/display-name
jest.mock("../../file/components/CollectionFilesContainer", () => (props: { collection: Collection }) => (
    <div data-testid="COLLECTION_FILES_CONTAINER_MOCK">
        <div>collectionId: {props.collection.id}</div>
    </div>
))

const startPath = `/collection/${exampleCollectionId}`
const routePathTemplate = "/collection/:collectionId"

const renderWithPath = renderWithRoute(routePathTemplate)

describe("Single collection container", () => {
    beforeEach(() => {
        window.history.replaceState({}, "", startPath)
    })

    it("should render collection view", () => {
        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const view = screen.getByTestId("COLLECTION_FILES_CONTAINER_MOCK")
        expect(view.textContent).toEqual(`collectionId: ${exampleCollectionId}`)
    })

    it("should render error message", () => {
        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{
                    status: "FAILED",
                    params: exampleCollectionId,
                    error: new Error("Some error"),
                }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const view = screen.getByTestId("ALERT_BOX_MOCK")
        expect(view.textContent).toMatch("TITLE: single-collection-view:fetching-details-error-message")
        expect(view.textContent).toMatch("STATUS: error")
    })

    it("should render loader if fetch status is NOT_STARTED", () => {
        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "NOT_STARTED" }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader.textContent).toEqual("single-collection-view:loading-collection-data")
    })

    it("should render loader if fetch status is PENDING", () => {
        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "PENDING", params: exampleCollectionId }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader.textContent).toEqual("single-collection-view:loading-collection-data")
    })

    it("should render loader if collection not found", () => {
        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "FINISHED", params: exampleCollectionId, data: null }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader.textContent).toEqual("single-collection-view:loading-collection-data")
    })

    it("should render loader if fetched collection ID does not match current collection", () => {
        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{
                    status: "FINISHED",
                    params: "f32655b7-da2b-4e8e-a645-3f3f01c766e5",
                    data: { ...exampleCollection, id: "f32655b7-da2b-4e8e-a645-3f3f01c766e5" },
                }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader.textContent).toEqual("single-collection-view:loading-collection-data")
    })

    it("should fetch collection data on mount if status is NOT_STARTED", () => {
        const fetchCollectionMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "NOT_STARTED" }}
                fetchCollectionData={fetchCollectionMock}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchCollectionMock).toHaveBeenCalledTimes(1)
        expect(fetchCollectionMock).toHaveBeenCalledWith(exampleCollectionId)
    })

    it("should fetch collection data on mount if status is FAILED", () => {
        const fetchCollectionMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{
                    status: "FAILED",
                    params: exampleCollectionId,
                    error: new Error("Some error"),
                }}
                fetchCollectionData={fetchCollectionMock}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchCollectionMock).toHaveBeenCalledTimes(1)
        expect(fetchCollectionMock).toHaveBeenCalledWith(exampleCollectionId)
    })

    it("should not fetch collection data on mount if status is PENDING", () => {
        const fetchCollectionMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "PENDING", params: exampleCollectionId }}
                fetchCollectionData={fetchCollectionMock}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchCollectionMock).not.toHaveBeenCalled()
    })

    it("should not fetch collection data on mount if status is FINISHED", () => {
        const fetchCollectionMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{
                    status: "FINISHED",
                    params: exampleCollectionId,
                    data: exampleCollection,
                }}
                fetchCollectionData={fetchCollectionMock}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchCollectionMock).not.toHaveBeenCalled()
    })

    it("should fetch collection data on mount if current collection is different than fetched one", () => {
        const fetchCollectionMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{
                    status: "FINISHED",
                    params: "f32655b7-da2b-4e8e-a645-3f3f01c766e5",
                    data: { ...exampleCollection, id: "f32655b7-da2b-4e8e-a645-3f3f01c766e5" },
                }}
                fetchCollectionData={fetchCollectionMock}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchCollectionMock).toHaveBeenCalledTimes(1)
        expect(fetchCollectionMock).toHaveBeenCalledWith(exampleCollectionId)
    })

    it("should reset remove preferred collection status on mount", () => {
        const resetRemovePreferredCollectionStatusMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "NOT_STARTED" }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={resetRemovePreferredCollectionStatusMock}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(resetRemovePreferredCollectionStatusMock).toHaveBeenCalledTimes(1)
    })

    it("should reset remove preferred collection status on unmount", () => {
        const resetRemovePreferredCollectionStatusMock = jest.fn()

        const view = renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "NOT_STARTED" }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                removePreferredCollection={jest.fn()}
                resetRemovePreferredCollectionResult={resetRemovePreferredCollectionStatusMock}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        view.unmount()

        expect(resetRemovePreferredCollectionStatusMock).toHaveBeenCalledTimes(2)
    })

    it("should set active and preferred collection when collection data fetched", () => {
        const setActiveCollectionMock = jest.fn()
        const setPreferredCollectionMock = jest.fn()
        const removeActiveCollectionMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const historyPushMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                removePreferredCollection={removePreferredCollectionMock}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={removeActiveCollectionMock}
                t={tFunctionMock}
            />
        )
        expect(setActiveCollectionMock).toHaveBeenCalledTimes(1)
        expect(setActiveCollectionMock).toHaveBeenCalledWith(exampleCollectionId)
        expect(setPreferredCollectionMock).toHaveBeenCalledTimes(1)
        expect(setPreferredCollectionMock).toHaveBeenCalledWith(exampleCollectionId)
        expect(removeActiveCollectionMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(historyPushMock).not.toHaveBeenCalled()
    })

    it("should do nothing when collection data status is NOT_STARTED", () => {
        const setActiveCollectionMock = jest.fn()
        const setPreferredCollectionMock = jest.fn()
        const removeActiveCollectionMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const historyPushMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "NOT_STARTED" }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                removePreferredCollection={removePreferredCollectionMock}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={removeActiveCollectionMock}
                t={tFunctionMock}
            />
        )
        expect(setActiveCollectionMock).not.toHaveBeenCalled()
        expect(setPreferredCollectionMock).not.toHaveBeenCalled()
        expect(removeActiveCollectionMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(historyPushMock).not.toHaveBeenCalled()
    })

    it("should do nothing when collection data status is PENDING", () => {
        const setActiveCollectionMock = jest.fn()
        const setPreferredCollectionMock = jest.fn()
        const removeActiveCollectionMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const historyPushMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "PENDING", params: exampleCollectionId }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                removePreferredCollection={removePreferredCollectionMock}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={removeActiveCollectionMock}
                t={tFunctionMock}
            />
        )
        expect(setActiveCollectionMock).not.toHaveBeenCalled()
        expect(setPreferredCollectionMock).not.toHaveBeenCalled()
        expect(removeActiveCollectionMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(historyPushMock).not.toHaveBeenCalled()
    })

    it("should do nothing when collection data status is FAILED", () => {
        const setActiveCollectionMock = jest.fn()
        const setPreferredCollectionMock = jest.fn()
        const removeActiveCollectionMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const historyPushMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{
                    status: "FAILED",
                    params: exampleCollectionId,
                    error: new Error("Some error"),
                }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                removePreferredCollection={removePreferredCollectionMock}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={removeActiveCollectionMock}
                t={tFunctionMock}
            />
        )
        expect(setActiveCollectionMock).not.toHaveBeenCalled()
        expect(setPreferredCollectionMock).not.toHaveBeenCalled()
        expect(removeActiveCollectionMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(historyPushMock).not.toHaveBeenCalled()
    })

    it("should do nothing when fetched data of another collection", () => {
        const setActiveCollectionMock = jest.fn()
        const setPreferredCollectionMock = jest.fn()
        const removeActiveCollectionMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const historyPushMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{
                    status: "FINISHED",
                    params: "f32655b7-da2b-4e8e-a645-3f3f01c766e5",
                    data: { ...exampleCollection, id: "f32655b7-da2b-4e8e-a645-3f3f01c766e5" },
                }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                removePreferredCollection={removePreferredCollectionMock}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={removeActiveCollectionMock}
                t={tFunctionMock}
            />
        )
        expect(setActiveCollectionMock).not.toHaveBeenCalled()
        expect(setPreferredCollectionMock).not.toHaveBeenCalled()
        expect(removeActiveCollectionMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(historyPushMock).not.toHaveBeenCalled()
    })

    it("should remove preferred and active collection if fetched data of non existing collection", () => {
        const setActiveCollectionMock = jest.fn()
        const setPreferredCollectionMock = jest.fn()
        const removeActiveCollectionMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "FINISHED", params: exampleCollectionId, data: null }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                removePreferredCollection={removePreferredCollectionMock}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "NOT_STARTED" }}
                removeActiveCollection={removeActiveCollectionMock}
                t={tFunctionMock}
            />
        )

        expect(setActiveCollectionMock).not.toHaveBeenCalled()
        expect(setPreferredCollectionMock).not.toHaveBeenCalled()
        expect(removeActiveCollectionMock).toHaveBeenCalledTimes(1)
        expect(removePreferredCollectionMock).toHaveBeenCalledTimes(1)
        expect(window.location.pathname).toBe(startPath)
    })

    it("should redirect to home if remove preferred collection finished", () => {
        const setActiveCollectionMock = jest.fn()
        const setPreferredCollectionMock = jest.fn()
        const removeActiveCollectionMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()

        renderWithPath(
            <SingleCollectionContainer
                fetchCollectionResult={{ status: "NOT_STARTED" }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                removePreferredCollection={removePreferredCollectionMock}
                resetRemovePreferredCollectionResult={jest.fn()}
                removePreferredCollectionResult={{ status: "FINISHED", params: undefined, data: undefined }}
                removeActiveCollection={removeActiveCollectionMock}
                t={tFunctionMock}
            />
        )
        // const rrr = renderWithStoreAndRouter(unknown, )
        expect(setActiveCollectionMock).not.toHaveBeenCalled()
        expect(setPreferredCollectionMock).not.toHaveBeenCalled()
        expect(removeActiveCollectionMock).not.toHaveBeenCalled()
        expect(removePreferredCollectionMock).not.toHaveBeenCalled()
        expect(window.location.pathname).toBe("/")
    })
})
