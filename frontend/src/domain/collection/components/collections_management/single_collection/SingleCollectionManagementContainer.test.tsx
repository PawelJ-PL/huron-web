import { screen } from "@testing-library/react"
import React from "react"
import { exampleCollection, exampleCollectionId } from "../../../../../testutils/constants/collection"
import { exampleUserId, exampleUserPublicData } from "../../../../../testutils/constants/user"
import { renderWithRoute } from "../../../../../testutils/helpers"
import { tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { Collection } from "../../../types/Collection"
import { CollectionPermission } from "../../../types/CollectionPermission"
import { SingleCollectionManagementContainer } from "./SingleCollectionManagementContainer"

jest.mock("./SingleCollectionManagementPage", () =>
    // eslint-disable-next-line react/display-name
    (props: { collection: Collection; myPermissions: CollectionPermission[]; members?: Record<string, CollectionPermission[]> }) => (
        <div data-testid="COLLECTION_MANAGEMENT_PAGE_PAGE_MOCK">
            <div data-testid="COLLECTION_MANAGEMENT_PAGE_COLLECTION">{JSON.stringify(props.collection)}</div>
            <div data-testid="COLLECTION_MANAGEMENT_PAGE_MY_PERMISSION">{JSON.stringify(props.myPermissions)}</div>
            <div data-testid="COLLECTION_MANAGEMENT_PAGE_MEMBERS">{JSON.stringify(props.members)}</div>
        </div>
    )
)

jest.mock("../../../../../application/components/common/UnexpectedErrorMessage")

jest.mock("../../../../../application/components/common/Loader")

// eslint-disable-next-line react/display-name
jest.mock("../../../../../application/components/common/EmptyPlaceholder", () => (props: { text: string }) => (
    <div data-testid="EMPTY_PLACEHOLDER_MOCK">{props.text}</div>
))

const startPath = `/collection/${exampleCollectionId}/manage`
const routePathTemplate = "/collection/:collectionId/manage"

const renderWithPath = renderWithRoute(routePathTemplate)

describe("Single collection management container", () => {
    beforeEach(() => {
        window.history.replaceState({}, "", startPath)
    })

    describe("Mount and unmount", () => {
        it("should fetch collection data", () => {
            const fetchCollectionMock = jest.fn()

            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "NOT_STARTED" }}
                    members={{ status: "NOT_STARTED" }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={fetchCollectionMock}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            expect(fetchCollectionMock).toHaveBeenCalledTimes(1)
            expect(fetchCollectionMock).toHaveBeenCalledWith(exampleCollectionId)
        })

        it("should not fetch collection data if status is PENDING with the same collectionId", () => {
            const fetchCollectionMock = jest.fn()

            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "PENDING", params: exampleCollectionId }}
                    members={{ status: "NOT_STARTED" }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={fetchCollectionMock}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            expect(fetchCollectionMock).not.toHaveBeenCalled()
        })

        it("should fetch collection data if status is PENDING with different collectionId", () => {
            const fetchCollectionMock = jest.fn()

            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "PENDING", params: "different-collection-id" }}
                    members={{ status: "NOT_STARTED" }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={fetchCollectionMock}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            expect(fetchCollectionMock).toHaveBeenCalledTimes(1)
            expect(fetchCollectionMock).toHaveBeenCalledWith(exampleCollectionId)
        })

        it("should fetch collection members", () => {
            const fetchMembersMock = jest.fn()

            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "NOT_STARTED" }}
                    members={{ status: "NOT_STARTED" }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={fetchMembersMock}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            expect(fetchMembersMock).toHaveBeenCalledTimes(1)
            expect(fetchMembersMock).toHaveBeenCalledWith(exampleCollectionId)
        })

        it("should not fetch collection members if status is PENDING with the same collectionId", () => {
            const fetchMembersMock = jest.fn()

            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "NOT_STARTED" }}
                    members={{ status: "PENDING", params: exampleCollectionId }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={fetchMembersMock}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            expect(fetchMembersMock).not.toHaveBeenCalled()
        })

        it("should fetch collection members if status is PENDING with different collectionId", () => {
            const fetchMembersMock = jest.fn()

            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "NOT_STARTED" }}
                    members={{ status: "PENDING", params: "different-collection-id" }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={fetchMembersMock}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            expect(fetchMembersMock).toHaveBeenCalledTimes(1)
            expect(fetchMembersMock).toHaveBeenCalledWith(exampleCollectionId)
        })

        it("should reset members data on unmount", () => {
            const resetMembersMock = jest.fn()

            const view = renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "NOT_STARTED" }}
                    members={{ status: "PENDING", params: "different-collection-id" }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={resetMembersMock}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            expect(resetMembersMock).not.toHaveBeenCalled()

            view.unmount()

            expect(resetMembersMock).toHaveBeenCalledTimes(1)
        })
    })

    describe("Effects", () => {
        it("should fetch current user permissions if missing from members list", () => {
            const fetchMyPermissionsMock = jest.fn()

            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                    members={{ status: "FINISHED", params: exampleCollectionId, data: { otherUser: ["CreateFile"] } }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={fetchMyPermissionsMock}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            expect(fetchMyPermissionsMock).toHaveBeenCalledTimes(1)
            expect(fetchMyPermissionsMock).toHaveBeenCalledWith(exampleCollectionId)
        })

        it("should not fetch current user permissions if collection details not fetched yet", () => {
            const fetchMyPermissionsMock = jest.fn()

            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "PENDING", params: exampleCollectionId }}
                    members={{ status: "FINISHED", params: exampleCollectionId, data: { otherUser: ["CreateFile"] } }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={fetchMyPermissionsMock}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            expect(fetchMyPermissionsMock).not.toHaveBeenCalled()
        })

        it("should not fetch current user permissions if members list not fetched yet", () => {
            const fetchMyPermissionsMock = jest.fn()

            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                    members={{ status: "PENDING", params: exampleCollectionId }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={fetchMyPermissionsMock}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            expect(fetchMyPermissionsMock).not.toHaveBeenCalled()
        })

        it("should not fetch current user permissions if exists in members list", () => {
            const fetchMyPermissionsMock = jest.fn()

            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                    members={{
                        status: "FINISHED",
                        params: exampleCollectionId,
                        data: { otherUser: ["CreateFile"], [exampleUserId]: ["ManageCollection"] },
                    }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={fetchMyPermissionsMock}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            expect(fetchMyPermissionsMock).not.toHaveBeenCalled()
        })

        it("should resolve unknown users details", () => {
            const fetchMemberDetailsMock = jest.fn()

            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                    members={{
                        status: "FINISHED",
                        params: exampleCollectionId,
                        data: { u1: ["ManageCollection"], u2: [], u3: ["ReadFile"], u4: [], u5: ["CreateFile"] },
                    }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{
                        u1: { status: "FINISHED", params: exampleUserId, data: exampleUserPublicData },
                        u3: { status: "FINISHED", params: exampleUserId, data: exampleUserPublicData },
                        u5: { status: "FINISHED", params: exampleUserId, data: exampleUserPublicData },
                    }}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={fetchMemberDetailsMock}
                    t={tFunctionMock}
                />
            )

            expect(fetchMemberDetailsMock).toHaveBeenCalledTimes(1)
            expect(fetchMemberDetailsMock).toHaveBeenCalledWith(["u2", "u4"])
        })
    })

    describe("Render", () => {
        it("should render collection management page if my permissions can be found in members", () => {
            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                    members={{
                        status: "FINISHED",
                        params: exampleCollectionId,
                        data: { [exampleUserId]: ["CreateFile"], u2: ["ModifyFile"] },
                    }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const collectionDetailsSection = screen.getByTestId("COLLECTION_MANAGEMENT_PAGE_COLLECTION")
            const myPermissionsSection = screen.getByTestId("COLLECTION_MANAGEMENT_PAGE_MY_PERMISSION")
            const membersSection = screen.getByTestId("COLLECTION_MANAGEMENT_PAGE_MEMBERS")

            expect(collectionDetailsSection.textContent).toBe(JSON.stringify(exampleCollection))
            expect(myPermissionsSection.textContent).toBe(JSON.stringify(["CreateFile"]))
            expect(membersSection.textContent).toBe(
                JSON.stringify({ [exampleUserId]: ["CreateFile"], u2: ["ModifyFile"] })
            )
        })

        it("should render collection management page if my permissions was fetched explicitly", () => {
            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                    members={{
                        status: "FINISHED",
                        params: exampleCollectionId,
                        data: { u2: ["ModifyFile"] },
                    }}
                    myPermissions={{ status: "FINISHED", params: exampleCollectionId, data: ["CreateFile"] }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const collectionDetailsSection = screen.getByTestId("COLLECTION_MANAGEMENT_PAGE_COLLECTION")
            const myPermissionsSection = screen.getByTestId("COLLECTION_MANAGEMENT_PAGE_MY_PERMISSION")
            const membersSection = screen.getByTestId("COLLECTION_MANAGEMENT_PAGE_MEMBERS")

            expect(collectionDetailsSection.textContent).toBe(JSON.stringify(exampleCollection))
            expect(myPermissionsSection.textContent).toBe(JSON.stringify(["CreateFile"]))
            expect(membersSection.textContent).toBe(JSON.stringify({ u2: ["ModifyFile"] }))
        })

        it("should render loader if unable to get my permissions because memebrs was fetched for different collection", () => {
            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                    members={{
                        status: "FINISHED",
                        params: "other-collection-id",
                        data: { [exampleUserId]: ["CreateFile"], u2: ["ModifyFile"] },
                    }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const loader = screen.getByTestId("LOADER_MOCK")

            expect(loader.textContent).toBe("collection-manage-page:loading-collection-data")
        })

        it("should render loader if unable to get my permissions because it was fetched for different collection", () => {
            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                    members={{
                        status: "FINISHED",
                        params: exampleCollectionId,
                        data: { u2: ["ModifyFile"] },
                    }}
                    myPermissions={{ status: "FINISHED", params: "other-collection-id", data: ["CreateFile"] }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const loader = screen.getByTestId("LOADER_MOCK")

            expect(loader.textContent).toBe("collection-manage-page:loading-collection-data")
        })

        it("should render placeholder if collection data contains null", () => {
            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "FINISHED", params: exampleCollectionId, data: null }}
                    members={{
                        status: "FINISHED",
                        params: exampleCollectionId,
                        data: { [exampleUserId]: ["CreateFile"], u2: ["ModifyFile"] },
                    }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={null}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const placeholder = screen.getByTestId("EMPTY_PLACEHOLDER_MOCK")

            expect(placeholder.textContent).toBe("collection-manage-page:collection-not-found-placeholder")
        })

        it("should render unexpected error", () => {
            renderWithPath(
                <SingleCollectionManagementContainer
                    collection={{ status: "FINISHED", params: exampleCollectionId, data: exampleCollection }}
                    members={{
                        status: "FAILED",
                        params: exampleCollectionId,
                        error: new Error("Some error"),
                    }}
                    myPermissions={{ status: "NOT_STARTED" }}
                    myId={exampleUserId}
                    knownUsers={{}}
                    fetchError={new Error("Some error")}
                    getCollectionDetails={jest.fn()}
                    getCollectionMembers={jest.fn()}
                    resetCollectionMembersResult={jest.fn()}
                    fetchMyPermissions={jest.fn()}
                    fetchMembersDetails={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const error = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")

            expect(error.textContent).toBe("Some error")
        })
    })
})
