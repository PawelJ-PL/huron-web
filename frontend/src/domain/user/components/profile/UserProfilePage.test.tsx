import { render, screen } from "@testing-library/react"
import React from "react"
import { exampleHashedEmail, exampleUserId, exampleUserNickname } from "../../../../testutils/constants/user"
import { tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { UserProfilePage } from "./UserProfilePage"
import * as chakraToast from "@chakra-ui/toast"
import { toastMock } from "../../../../testutils/mocks/toast-mock"

// eslint-disable-next-line react/display-name
jest.mock("./UserProfileView", () => () => <div data-testid="user-profile-view"></div>)

describe("User profile page", () => {
    describe("mount and unmount", () => {
        it("should reset refresh status on mount", () => {
            const resetRefreshStatus = jest.fn()
            render(
                <UserProfilePage
                    resetUpdateProfileState={jest.fn()}
                    refreshData={jest.fn()}
                    resetRefreshStatus={resetRefreshStatus}
                    t={tFunctionMock}
                    refreshDataStatus={"FINISHED"}
                    userData={{
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    }}
                />
            )
            expect(resetRefreshStatus).toHaveBeenCalledTimes(1)
        })

        it("should reset update profile status on mount", () => {
            const resetUpdateStatus = jest.fn()
            render(
                <UserProfilePage
                    resetUpdateProfileState={resetUpdateStatus}
                    refreshData={jest.fn()}
                    resetRefreshStatus={jest.fn()}
                    t={tFunctionMock}
                    refreshDataStatus={"FINISHED"}
                    userData={{
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    }}
                />
            )
            expect(resetUpdateStatus).toHaveBeenCalledTimes(1)
        })

        it("should refresh user data on mount", () => {
            const refreshData = jest.fn()
            render(
                <UserProfilePage
                    resetUpdateProfileState={jest.fn()}
                    refreshData={refreshData}
                    resetRefreshStatus={jest.fn()}
                    t={tFunctionMock}
                    refreshDataStatus={"FINISHED"}
                    userData={{
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    }}
                />
            )
            expect(refreshData).toHaveBeenCalledTimes(1)
        })

        it("should reset refresh status on unmount", () => {
            const resetRefreshStatus = jest.fn()
            const view = render(
                <UserProfilePage
                    resetUpdateProfileState={jest.fn()}
                    refreshData={jest.fn()}
                    resetRefreshStatus={resetRefreshStatus}
                    t={tFunctionMock}
                    refreshDataStatus={"FINISHED"}
                    userData={{
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    }}
                />
            )
            view.unmount()
            expect(resetRefreshStatus).toHaveBeenCalledTimes(2)
        })
    })

    describe("toast message", () => {
        it("should be shown if refresh data failed", () => {
            const toast = jest.fn()
            const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))

            render(
                <UserProfilePage
                    resetUpdateProfileState={jest.fn()}
                    refreshData={jest.fn()}
                    resetRefreshStatus={jest.fn()}
                    t={tFunctionMock}
                    refreshDataStatus={"FAILED"}
                    userData={{
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    }}
                />
            )
            expect(toast).toHaveBeenCalledTimes(1)
            expect(toast).toHaveBeenCalledWith({
                isClosable: true,
                duration: 7000,
                id: "refresh-data-failed",
                status: "warning",
                title: "profile-page:refresh-failed-toast.title",
                description: "profile-page:refresh-failed-toast.description",
            })

            useToastMock.mockRestore()
        })
    })

    describe("render", () => {
        it("should render loader if render status is PENDING", () => {
            render(
                <UserProfilePage
                    resetUpdateProfileState={jest.fn()}
                    refreshData={jest.fn()}
                    resetRefreshStatus={jest.fn()}
                    t={tFunctionMock}
                    refreshDataStatus={"PENDING"}
                    userData={{
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    }}
                />
            )
            const loader = screen.getByText("profile-page:loading-user-data")
            expect(loader).toBeInTheDocument()
        })

        it("should render profile view if render status is FINISHED", () => {
            render(
                <UserProfilePage
                    resetUpdateProfileState={jest.fn()}
                    refreshData={jest.fn()}
                    resetRefreshStatus={jest.fn()}
                    t={tFunctionMock}
                    refreshDataStatus={"FINISHED"}
                    userData={{
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    }}
                />
            )
            const userProfile = screen.getByTestId("user-profile-view")
            expect(userProfile).toBeInTheDocument()
        })

        it("should render profile view if render status is FAILED", () => {
            render(
                <UserProfilePage
                    resetUpdateProfileState={jest.fn()}
                    refreshData={jest.fn()}
                    resetRefreshStatus={jest.fn()}
                    t={tFunctionMock}
                    refreshDataStatus={"FAILED"}
                    userData={{
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    }}
                />
            )
            const userProfile = screen.getByTestId("user-profile-view")
            expect(userProfile).toBeInTheDocument()
        })
    })
})
