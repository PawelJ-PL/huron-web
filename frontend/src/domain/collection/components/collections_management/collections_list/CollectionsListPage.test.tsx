import { render, screen } from "@testing-library/react"
import React from "react"
import { exampleCollection } from "../../../../../testutils/constants/collection"
import { tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { initialCollectionsListFilter } from "../../../store/Reducers"
import { CollectionsListPage } from "./CollectionsListPage"
import * as chakraToast from "@chakra-ui/toast"
import { toastMock } from "../../../../../testutils/mocks/toast-mock"
import { Collection } from "../../../types/Collection"

// eslint-disable-next-line react/display-name
jest.mock("../../AutoCreateCollectionModal", () => () => <div />)

// eslint-disable-next-line react/display-name
jest.mock("./FiltersPanel", () => () => <div />)

// eslint-disable-next-line react/display-name
jest.mock("./CollectionListEntry", () => (props: { collection: Collection }) => (
    <tr data-testid="COLLECTION_ENTRY_MOCK">
        <td>{props.collection.id}</td>
    </tr>
))

describe("Collections list page", () => {
    describe("effects", () => {
        it("should show error toast on update failed", () => {
            const toast = jest.fn()
            const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))

            render(
                <CollectionsListPage
                    collections={[exampleCollection]}
                    t={tFunctionMock}
                    listFilter={initialCollectionsListFilter}
                    updatingDetails={false}
                    updateDetailsFailed={true}
                />
            )

            expect(toast).toHaveBeenCalledTimes(1)
            expect(toast).toHaveBeenCalledWith({
                id: "update-failed",
                isClosable: true,
                status: "error",
                title: "collections-list-page:updating-details-failed-toast-message",
            })
            useToastMock.mockRestore()
        })

        it("should not show error toast if there was no error", () => {
            const toast = jest.fn()
            const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))

            render(
                <CollectionsListPage
                    collections={[exampleCollection]}
                    t={tFunctionMock}
                    listFilter={initialCollectionsListFilter}
                    updatingDetails={false}
                    updateDetailsFailed={false}
                />
            )

            expect(toast).not.toHaveBeenCalled()
            useToastMock.mockRestore()
        })
    })

    describe("filters", () => {
        const collection1 = exampleCollection
        const collection2 = { ...exampleCollection, id: "c2", isAccepted: false, name: "foo" }
        const collection3 = { ...exampleCollection, id: "c3", isAccepted: false, name: "second-collection" }
        const collection4 = { ...exampleCollection, id: "c4", isAccepted: true, name: "Another collection" }
        const collection5 = { ...exampleCollection, id: "c5", isAccepted: true, name: "bar" }

        const collections = [collection1, collection2, collection3, collection4, collection5]

        it("should apply all filters", () => {
            render(
                <CollectionsListPage
                    collections={collections}
                    t={tFunctionMock}
                    listFilter={{
                        nameFilter: "collection",
                        acceptanceFilter: { showAccepted: true, showNonAccepted: false },
                    }}
                    updatingDetails={false}
                    updateDetailsFailed={true}
                />
            )

            const entries = screen.queryAllByTestId("COLLECTION_ENTRY_MOCK").map((entry) => entry.textContent)
            expect(entries).toStrictEqual([collection1.id, collection4.id])
        })

        it("should apply name filter only", () => {
            render(
                <CollectionsListPage
                    collections={collections}
                    t={tFunctionMock}
                    listFilter={{
                        nameFilter: "collection",
                        acceptanceFilter: { showAccepted: true, showNonAccepted: true },
                    }}
                    updatingDetails={false}
                    updateDetailsFailed={true}
                />
            )

            const entries = screen.queryAllByTestId("COLLECTION_ENTRY_MOCK").map((entry) => entry.textContent)
            expect(entries).toStrictEqual([collection1.id, collection3.id, collection4.id])
        })

        it("should show all accepted only", () => {
            render(
                <CollectionsListPage
                    collections={collections}
                    t={tFunctionMock}
                    listFilter={{
                        nameFilter: "",
                        acceptanceFilter: { showAccepted: true, showNonAccepted: false },
                    }}
                    updatingDetails={false}
                    updateDetailsFailed={true}
                />
            )

            const entries = screen.queryAllByTestId("COLLECTION_ENTRY_MOCK").map((entry) => entry.textContent)
            expect(entries).toStrictEqual([collection1.id, collection4.id, collection5.id])
        })

        it("should show all non accepted only", () => {
            render(
                <CollectionsListPage
                    collections={collections}
                    t={tFunctionMock}
                    listFilter={{
                        nameFilter: "",
                        acceptanceFilter: { showAccepted: false, showNonAccepted: true },
                    }}
                    updatingDetails={false}
                    updateDetailsFailed={true}
                />
            )

            const entries = screen.queryAllByTestId("COLLECTION_ENTRY_MOCK").map((entry) => entry.textContent)
            expect(entries).toStrictEqual([collection2.id, collection3.id])
        })
    })
})
