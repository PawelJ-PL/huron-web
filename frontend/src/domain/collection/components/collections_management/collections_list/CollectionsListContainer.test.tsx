import { render, screen } from "@testing-library/react"
import React from "react"
import { exampleCollection } from "../../../../../testutils/constants/collection"
import { exampleUserId, exampleUserPublicData } from "../../../../../testutils/constants/user"
import { tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { Collection } from "../../../types/Collection"
import { CollectionsListContainer } from "./CollectionsListContainer"

// eslint-disable-next-line react/display-name
jest.mock("./CollectionsListPage", () => (props: { collections: Collection[] }) => (
    <div data-testid="COLLECTIONS_LIST_PAGE_MOCK">{JSON.stringify(props.collections)}</div>
))

jest.mock("../../../../../application/components/common/UnexpectedErrorMessage")

jest.mock("../../../../../application/components/common/Loader")

describe("Collections list container", () => {
    describe("mount and unmount", () => {
        it("should fetch collections on mount", () => {
            const fetchCollectionsMock = jest.fn()

            render(
                <CollectionsListContainer
                    t={tFunctionMock}
                    collections={{ status: "FINISHED", params: false, data: [exampleCollection] }}
                    fetchAllCollections={fetchCollectionsMock}
                    resetCollectionsList={jest.fn()}
                    knownUsers={{}}
                    fetchAuthors={jest.fn()}
                />
            )

            expect(fetchCollectionsMock).toHaveBeenCalledTimes(1)
        })

        it("should fetch collections on mount if status is PENDING and onlyAccepted set to true", () => {
            const fetchCollectionsMock = jest.fn()

            render(
                <CollectionsListContainer
                    t={tFunctionMock}
                    collections={{ status: "PENDING", params: true }}
                    fetchAllCollections={fetchCollectionsMock}
                    resetCollectionsList={jest.fn()}
                    knownUsers={{}}
                    fetchAuthors={jest.fn()}
                />
            )

            expect(fetchCollectionsMock).toHaveBeenCalledTimes(1)
        })

        it("should not fetch collections on mount if status is PENDING and onlyAccepted set to false", () => {
            const fetchCollectionsMock = jest.fn()

            render(
                <CollectionsListContainer
                    t={tFunctionMock}
                    collections={{ status: "PENDING", params: false }}
                    fetchAllCollections={fetchCollectionsMock}
                    resetCollectionsList={jest.fn()}
                    knownUsers={{}}
                    fetchAuthors={jest.fn()}
                />
            )

            expect(fetchCollectionsMock).not.toHaveBeenCalled()
        })

        it("should fetch collections on mount if status is NOT_STARTED", () => {
            const fetchCollectionsMock = jest.fn()

            render(
                <CollectionsListContainer
                    t={tFunctionMock}
                    collections={{ status: "NOT_STARTED" }}
                    fetchAllCollections={fetchCollectionsMock}
                    resetCollectionsList={jest.fn()}
                    knownUsers={{}}
                    fetchAuthors={jest.fn()}
                />
            )

            expect(fetchCollectionsMock).toHaveBeenCalledTimes(1)
        })

        it("should reset collections data on unmount", () => {
            const resetCollectionsMock = jest.fn()

            const view = render(
                <CollectionsListContainer
                    t={tFunctionMock}
                    collections={{ status: "FINISHED", params: false, data: [exampleCollection] }}
                    fetchAllCollections={jest.fn()}
                    resetCollectionsList={resetCollectionsMock}
                    knownUsers={{}}
                    fetchAuthors={jest.fn()}
                />
            )

            view.unmount()

            expect(resetCollectionsMock).toHaveBeenCalledTimes(1)
        })
    })

    describe("effects", () => {
        it("should fetch missing user details when collections are fetched", () => {
            const fetchAuthorsMock = jest.fn()
            const collections = [
                exampleCollection,
                { ...exampleCollection, id: "c2", owner: "u2" },
                { ...exampleCollection, id: "c3", owner: "u3" },
                { ...exampleCollection, id: "c4", owner: "u4" },
            ]
            const knownUsers = {
                [exampleUserId]: { status: "FINISHED" as const, params: exampleUserId, data: exampleUserPublicData },
                u3: { status: "FINISHED" as const, params: "u3", data: { ...exampleUserPublicData, userId: "u3" } },
            }

            render(
                <CollectionsListContainer
                    t={tFunctionMock}
                    collections={{ status: "FINISHED", params: false, data: collections }}
                    fetchAllCollections={jest.fn()}
                    resetCollectionsList={jest.fn()}
                    knownUsers={knownUsers}
                    fetchAuthors={fetchAuthorsMock}
                />
            )
            expect(fetchAuthorsMock).toHaveBeenCalledTimes(1)
            expect(fetchAuthorsMock).toHaveBeenCalledWith(["u2", "u4"])
        })
    })

    describe("render", () => {
        it("should render collections list page", () => {
            render(
                <CollectionsListContainer
                    t={tFunctionMock}
                    collections={{ status: "FINISHED", params: false, data: [exampleCollection] }}
                    fetchAllCollections={jest.fn()}
                    resetCollectionsList={jest.fn()}
                    knownUsers={{}}
                    fetchAuthors={jest.fn()}
                />
            )

            const collectionsListPage = screen.getByTestId("COLLECTIONS_LIST_PAGE_MOCK")

            expect(collectionsListPage.textContent).toMatch(JSON.stringify([exampleCollection]))
        })

        it("should render unexpected error", () => {
            render(
                <CollectionsListContainer
                    t={tFunctionMock}
                    collections={{ status: "FAILED", params: false, error: new Error("Some error") }}
                    fetchAllCollections={jest.fn()}
                    resetCollectionsList={jest.fn()}
                    knownUsers={{}}
                    fetchAuthors={jest.fn()}
                />
            )

            const errorMessage = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")

            expect(errorMessage.textContent).toBe("Some error")
        })

        it("should render loader", () => {
            render(
                <CollectionsListContainer
                    t={tFunctionMock}
                    collections={{ status: "PENDING", params: false }}
                    fetchAllCollections={jest.fn()}
                    resetCollectionsList={jest.fn()}
                    knownUsers={{}}
                    fetchAuthors={jest.fn()}
                />
            )

            const loader = screen.getByTestId("LOADER_MOCK")

            expect(loader.textContent).toMatch("collections-list-page:loader-text")
        })
    })
})
