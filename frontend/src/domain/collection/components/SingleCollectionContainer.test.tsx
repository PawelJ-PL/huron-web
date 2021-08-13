import { render, screen } from "@testing-library/react"
import React from "react"
import { exampleCollection, exampleCollectionId } from "../../../testutils/constants/collection"
import { tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { historyMock } from "../../../testutils/mocks/router-mock"
import { SingleCollectionContainer } from "./SingleCollectionContainer"

jest.mock("../../../application/components/common/AlertBox")

jest.mock("../../../application/components/common/Loader")

const exampleRouteMatch = {
    params: { collectionId: exampleCollectionId },
    isExact: true,
    path: "/collection/:collectionId",
    url: `/collection/${exampleCollectionId}`,
}

describe("Single collection container", () => {
    it("should render collection view", () => {
        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={jest.fn()}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const view = screen.getByTestId("TEMPORARY-COLLECTION-VIEW")
        expect(view.textContent).toEqual(JSON.stringify(exampleCollection))
    })

    it("should render error message", () => {
        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{
                    status: "FAILED",
                    params: exampleCollectionId,
                    error: new Error("Some error"),
                }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={jest.fn()}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const view = screen.getByTestId("ALERT_BOX_MOCK")
        expect(view.textContent).toMatch("TITLE: single-collection-view:fetching-details-error-message")
        expect(view.textContent).toMatch("STATUS: error")
    })

    it("should render loader if fetch status is NOT_STARTED", () => {
        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{ status: "NOT_STARTED" }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={jest.fn()}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader.textContent).toEqual("single-collection-view:loading-collection-data")
    })

    it("should render loader if fetch status is PENDING", () => {
        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{ status: "PENDING", params: exampleCollectionId }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={jest.fn()}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader.textContent).toEqual("single-collection-view:loading-collection-data")
    })

    it("should render loader if collection not found", () => {
        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{ status: "FINISHED", params: exampleCollectionId, data: null }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={jest.fn()}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader.textContent).toEqual("single-collection-view:loading-collection-data")
    })

    it("should render loader if fetched collection ID does not match current collection", () => {
        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{
                    status: "FINISHED",
                    params: "f32655b7-da2b-4e8e-a645-3f3f01c766e5",
                    data: { ...exampleCollection, id: "f32655b7-da2b-4e8e-a645-3f3f01c766e5" },
                }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={jest.fn()}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )
        const loader = screen.getByTestId("LOADER_MOCK")
        expect(loader.textContent).toEqual("single-collection-view:loading-collection-data")
    })

    it("should fetch collection data on mount if status is NOT_STARTED", () => {
        const fetchCollectionMock = jest.fn()

        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{ status: "NOT_STARTED" }}
                fetchCollectionData={fetchCollectionMock}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={jest.fn()}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchCollectionMock).toHaveBeenCalledTimes(1)
        expect(fetchCollectionMock).toHaveBeenCalledWith(exampleCollectionId)
    })

    it("should fetch collection data on mount if status is FAILED", () => {
        const fetchCollectionMock = jest.fn()

        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{
                    status: "FAILED",
                    params: exampleCollectionId,
                    error: new Error("Some error"),
                }}
                fetchCollectionData={fetchCollectionMock}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={jest.fn()}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchCollectionMock).toHaveBeenCalledTimes(1)
        expect(fetchCollectionMock).toHaveBeenCalledWith(exampleCollectionId)
    })

    it("should not fetch collection data on mount if status is PENDING", () => {
        const fetchCollectionMock = jest.fn()

        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{ status: "PENDING", params: exampleCollectionId }}
                fetchCollectionData={fetchCollectionMock}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={jest.fn()}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchCollectionMock).not.toHaveBeenCalled()
    })

    it("should not fetch collection data on mount if status is FINISHED", () => {
        const fetchCollectionMock = jest.fn()

        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{
                    status: "FINISHED",
                    params: exampleCollectionId,
                    data: exampleCollection,
                }}
                fetchCollectionData={fetchCollectionMock}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={jest.fn()}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchCollectionMock).not.toHaveBeenCalled()
    })

    it("should fetch collection data on mount if current collection is differen than fetched one", () => {
        const fetchCollectionMock = jest.fn()

        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{
                    status: "FINISHED",
                    params: "f32655b7-da2b-4e8e-a645-3f3f01c766e5",
                    data: { ...exampleCollection, id: "f32655b7-da2b-4e8e-a645-3f3f01c766e5" },
                }}
                fetchCollectionData={fetchCollectionMock}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={jest.fn()}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchCollectionMock).toHaveBeenCalledTimes(1)
        expect(fetchCollectionMock).toHaveBeenCalledWith(exampleCollectionId)
    })

    it("should reset collection data on unmount", () => {
        const resetCollectionMock = jest.fn()

        const element = render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock()}
                fetchCollectionResult={{ status: "NOT_STARTED" }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={jest.fn()}
                setPreferredCollection={jest.fn()}
                resetCollectionData={resetCollectionMock}
                removePreferredCollection={jest.fn()}
                removeActiveCollection={jest.fn()}
                t={tFunctionMock}
            />
        )

        element.unmount()

        expect(resetCollectionMock).toHaveBeenCalledTimes(1)
    })

    it("should set active and preferred collection when collection data fetched", () => {
        const setActiveCollectionMock = jest.fn()
        const setPreferredCollectionMock = jest.fn()
        const removeActiveCollectionMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const historyPushMock = jest.fn()

        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock({ push: historyPushMock })}
                fetchCollectionResult={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                resetCollectionData={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
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

        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock({ push: historyPushMock })}
                fetchCollectionResult={{ status: "NOT_STARTED" }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                resetCollectionData={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
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

        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock({ push: historyPushMock })}
                fetchCollectionResult={{ status: "PENDING", params: exampleCollectionId }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                resetCollectionData={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
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

        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock({ push: historyPushMock })}
                fetchCollectionResult={{
                    status: "FAILED",
                    params: exampleCollectionId,
                    error: new Error("Some error"),
                }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                resetCollectionData={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
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

        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock({ push: historyPushMock })}
                fetchCollectionResult={{
                    status: "FINISHED",
                    params: "f32655b7-da2b-4e8e-a645-3f3f01c766e5",
                    data: { ...exampleCollection, id: "f32655b7-da2b-4e8e-a645-3f3f01c766e5" },
                }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                resetCollectionData={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
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

    it("should remove preferred and active collection and redirect if fetched data of non existing collection", () => {
        const setActiveCollectionMock = jest.fn()
        const setPreferredCollectionMock = jest.fn()
        const removeActiveCollectionMock = jest.fn()
        const removePreferredCollectionMock = jest.fn()
        const historyPushMock = jest.fn()

        render(
            <SingleCollectionContainer
                match={exampleRouteMatch}
                history={historyMock({ push: historyPushMock })}
                fetchCollectionResult={{ status: "FINISHED", params: exampleCollectionId, data: null }}
                fetchCollectionData={jest.fn()}
                setActiveCollection={setActiveCollectionMock}
                setPreferredCollection={setPreferredCollectionMock}
                resetCollectionData={jest.fn()}
                removePreferredCollection={removePreferredCollectionMock}
                removeActiveCollection={removeActiveCollectionMock}
                t={tFunctionMock}
            />
        )
        expect(setActiveCollectionMock).not.toHaveBeenCalled()
        expect(setPreferredCollectionMock).not.toHaveBeenCalled()
        expect(removeActiveCollectionMock).toHaveBeenCalledTimes(1)
        expect(removePreferredCollectionMock).toHaveBeenCalledTimes(1)
        expect(historyPushMock).toHaveBeenCalledTimes(1)
        expect(historyPushMock).toHaveBeenCalledWith("/")
    })
})
