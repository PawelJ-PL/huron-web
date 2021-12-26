import React from "react"
import { screen } from "@testing-library/react"
import {
    exampleUserEmail,
    exampleUserId,
    exampleUserNickname,
    exampleUserPublicData,
} from "../../../../testutils/constants/user"
import { renderWithRoute } from "../../../../testutils/helpers"
import { tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { UserPublicDataContainer } from "./UserPublicDataContainer"
import { UserPublicData } from "../../types/UserPublicData"

// eslint-disable-next-line react/display-name
jest.mock("./UserPublicDataPage", () => (props: { userPublicData: UserPublicData; self: boolean }) => (
    <div data-testid="USER_PUBLIC_DATA_PAGE_MOCK">
        <div>self: {JSON.stringify(props.self)}</div>
        <div>data: {JSON.stringify(props.userPublicData)}</div>
    </div>
))

jest.mock("../../../../application/components/common/UnexpectedErrorMessage")

jest.mock("../../../../application/components/common/Loader")

jest.mock("../../../../application/components/common/EmptyPlaceholder")

const pathTemplate = "/user/:userId"
// eslint-disable-next-line testing-library/render-result-naming-convention
const renderWithPath = renderWithRoute(pathTemplate)

describe("User public data container", () => {
    beforeEach(() => window.history.replaceState({}, "", `/user/${exampleUserId}`))

    describe("mount", () => {
        it("should fetch user data", () => {
            const fetchUserData = jest.fn()

            renderWithPath(
                <UserPublicDataContainer
                    t={tFunctionMock}
                    userDataResult={{ status: "NOT_STARTED" }}
                    meDataResult={{
                        status: "FINISHED",
                        params: void 0,
                        data: {
                            id: "anotherUserId",
                            emailHash: exampleUserEmail,
                            language: "pl",
                            nickName: exampleUserNickname,
                        },
                    }}
                    fetchUserPublicData={fetchUserData}
                />
            )

            expect(fetchUserData).toBeCalledTimes(1)
            expect(fetchUserData).toHaveBeenCalledWith(exampleUserId)
        })

        it("should not fetch user data if status is PENDING and userId match", () => {
            const fetchUserData = jest.fn()

            renderWithPath(
                <UserPublicDataContainer
                    t={tFunctionMock}
                    userDataResult={{ status: "PENDING", params: exampleUserId }}
                    meDataResult={{
                        status: "FINISHED",
                        params: void 0,
                        data: {
                            id: "anotherUserId",
                            emailHash: exampleUserEmail,
                            language: "pl",
                            nickName: exampleUserNickname,
                        },
                    }}
                    fetchUserPublicData={fetchUserData}
                />
            )

            expect(fetchUserData).not.toBeCalled()
        })

        it("should not fetch user data if status is FINISHED and userId match", () => {
            const fetchUserData = jest.fn()

            renderWithPath(
                <UserPublicDataContainer
                    t={tFunctionMock}
                    userDataResult={{ status: "FINISHED", params: exampleUserId, data: exampleUserPublicData }}
                    meDataResult={{
                        status: "FINISHED",
                        params: void 0,
                        data: {
                            id: "anotherUserId",
                            emailHash: exampleUserEmail,
                            language: "pl",
                            nickName: exampleUserNickname,
                        },
                    }}
                    fetchUserPublicData={fetchUserData}
                />
            )

            expect(fetchUserData).not.toBeCalled()
        })

        it("should fetch user data if userId does not match", () => {
            const fetchUserData = jest.fn()

            renderWithPath(
                <UserPublicDataContainer
                    t={tFunctionMock}
                    userDataResult={{ status: "FINISHED", params: "anotherUserId", data: exampleUserPublicData }}
                    meDataResult={{
                        status: "FINISHED",
                        params: void 0,
                        data: {
                            id: "anotherUserId",
                            emailHash: exampleUserEmail,
                            language: "pl",
                            nickName: exampleUserNickname,
                        },
                    }}
                    fetchUserPublicData={fetchUserData}
                />
            )

            expect(fetchUserData).toBeCalledTimes(1)
            expect(fetchUserData).toHaveBeenCalledWith(exampleUserId)
        })
    })

    describe("render", () => {
        it("should render unexpected error message if curent user data is not fetched", () => {
            renderWithPath(
                <UserPublicDataContainer
                    t={tFunctionMock}
                    userDataResult={{ status: "NOT_STARTED" }}
                    meDataResult={{ status: "NOT_STARTED" }}
                    fetchUserPublicData={jest.fn()}
                />
            )
            const errorMessage = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorMessage).toHaveTextContent("Current user data not available")
        })
        it("should render unexpected error message if fetch data failed", () => {
            renderWithPath(
                <UserPublicDataContainer
                    t={tFunctionMock}
                    userDataResult={{ status: "FAILED", params: exampleUserId, error: new Error("Some error") }}
                    meDataResult={{
                        status: "FINISHED",
                        params: void 0,
                        data: {
                            id: "anotherUserId",
                            emailHash: exampleUserEmail,
                            language: "pl",
                            nickName: exampleUserNickname,
                        },
                    }}
                    fetchUserPublicData={jest.fn()}
                />
            )
            const errorMessage = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorMessage).toHaveTextContent("Some error")
        })

        it("should render loader if fetch data status is PENDING", () => {
            renderWithPath(
                <UserPublicDataContainer
                    t={tFunctionMock}
                    userDataResult={{ status: "PENDING", params: exampleUserId }}
                    meDataResult={{
                        status: "FINISHED",
                        params: void 0,
                        data: {
                            id: "anotherUserId",
                            emailHash: exampleUserEmail,
                            language: "pl",
                            nickName: exampleUserNickname,
                        },
                    }}
                    fetchUserPublicData={jest.fn()}
                />
            )
            const loader = screen.getByTestId("LOADER_MOCK")
            expect(loader).toHaveTextContent("user-public-page:loading-user-data")
        })

        it("should render loader if fetch data status is NOT_STARTED", () => {
            renderWithPath(
                <UserPublicDataContainer
                    t={tFunctionMock}
                    userDataResult={{ status: "NOT_STARTED" }}
                    meDataResult={{
                        status: "FINISHED",
                        params: void 0,
                        data: {
                            id: "anotherUserId",
                            emailHash: exampleUserEmail,
                            language: "pl",
                            nickName: exampleUserNickname,
                        },
                    }}
                    fetchUserPublicData={jest.fn()}
                />
            )
            const loader = screen.getByTestId("LOADER_MOCK")
            expect(loader).toHaveTextContent("user-public-page:loading-user-data")
        })

        it("should render placeholder if user not found", () => {
            renderWithPath(
                <UserPublicDataContainer
                    t={tFunctionMock}
                    userDataResult={{ status: "FINISHED", params: exampleUserId, data: null }}
                    meDataResult={{
                        status: "FINISHED",
                        params: void 0,
                        data: {
                            id: "anotherUserId",
                            emailHash: exampleUserEmail,
                            language: "pl",
                            nickName: exampleUserNickname,
                        },
                    }}
                    fetchUserPublicData={jest.fn()}
                />
            )
            const placeholder = screen.getByTestId("EMPTY_PLACEHOLDER_MOCK")
            expect(placeholder).toHaveTextContent("user-public-page:user-not-found-message")
        })

        it("should render public user page", () => {
            renderWithPath(
                <UserPublicDataContainer
                    t={tFunctionMock}
                    userDataResult={{ status: "FINISHED", params: exampleUserId, data: exampleUserPublicData }}
                    meDataResult={{
                        status: "FINISHED",
                        params: void 0,
                        data: {
                            id: "anotherUserId",
                            emailHash: exampleUserEmail,
                            language: "pl",
                            nickName: exampleUserNickname,
                        },
                    }}
                    fetchUserPublicData={jest.fn()}
                />
            )
            const page = screen.getByTestId("USER_PUBLIC_DATA_PAGE_MOCK")
            expect(page).toHaveTextContent("self: false")
            expect(page).toHaveTextContent(`data: ${JSON.stringify(exampleUserPublicData)}`)
        })
    })
})
