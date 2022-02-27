import { render, screen } from "@testing-library/react"
import React from "react"
import { MemoryRouter } from "react-router-dom"
import { UserData } from "../domain/user/types/UserData"
import { exampleCollectionId } from "../testutils/constants/collection"
import { exampleHashedEmail, exampleUserId, exampleUserNickname } from "../testutils/constants/user"
import { renderWithRoute } from "../testutils/helpers"
import { i18nMock, tFunctionMock } from "../testutils/mocks/i18n-mock"
import { NotLoggedIn } from "./api/ApiError"
import { AuthDecisionContainer } from "./AuthDecisionContainer"
import { LOADER_PAGE } from "./pages/testids"
import { AppRoute } from "./router/AppRoute"
import { AsyncOperationResult } from "./store/async/AsyncOperationResult"

// eslint-disable-next-line react/display-name
jest.mock("./router/AppRouter", () => (props: { routes: AppRoute[] }) => (
    <div>
        {props.routes.map((r) => (
            <div key={r.path} data-testid={r.path}>
                {r.path}
            </div>
        ))}
    </div>
))

describe("Auth decision container", () => {
    describe("effects", () => {
        it("should fetch user data on mount", () => {
            const userData: AsyncOperationResult<void, UserData, Error> = { status: "NOT_STARTED" }
            const fetchUser = jest.fn()
            const clearPasswords = jest.fn()
            render(
                <MemoryRouter>
                    <AuthDecisionContainer
                        userData={userData}
                        fetchUserData={fetchUser}
                        clearPasswords={clearPasswords}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        tReady={true}
                        apiLogoutStatus={{ status: "NOT_STARTED" }}
                    />
                </MemoryRouter>
            )
            expect(fetchUser).toHaveBeenCalledTimes(1)
        })

        it("should clear passwords on not logged in error", () => {
            const userData: AsyncOperationResult<void, UserData, Error> = {
                status: "FAILED",
                params: void 0,
                error: new NotLoggedIn(),
            }
            const fetchUser = jest.fn()
            const clearPasswords = jest.fn()
            render(
                <MemoryRouter>
                    <AuthDecisionContainer
                        userData={userData}
                        fetchUserData={fetchUser}
                        clearPasswords={clearPasswords}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        tReady={true}
                        apiLogoutStatus={{ status: "NOT_STARTED" }}
                    />
                </MemoryRouter>
            )
            expect(clearPasswords).toHaveBeenCalledTimes(1)
        })

        it("should navigate to root on API logout", () => {
            const startPath = `/collection/${exampleCollectionId}`
            const routePathTemplate = "/collection/:collectionId"

            window.history.replaceState({}, "", startPath)

            const renderWithPath = renderWithRoute(routePathTemplate)

            const userData: AsyncOperationResult<void, UserData, Error> = { status: "NOT_STARTED" }
            renderWithPath(
                <AuthDecisionContainer
                    userData={userData}
                    fetchUserData={jest.fn()}
                    clearPasswords={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                    apiLogoutStatus={{ status: "FINISHED", params: undefined, data: undefined }}
                />
            )
            expect(window.location.pathname).toBe("/")
        })
    })

    describe("route decision", () => {
        it("should render guest routes on not logged in error", () => {
            const userData: AsyncOperationResult<void, UserData, Error> = {
                status: "FAILED",
                params: void 0,
                error: new NotLoggedIn(),
            }
            const fetchUser = jest.fn()
            const clearPasswords = jest.fn()
            render(
                <MemoryRouter>
                    <AuthDecisionContainer
                        userData={userData}
                        fetchUserData={fetchUser}
                        clearPasswords={clearPasswords}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        tReady={true}
                        apiLogoutStatus={{ status: "NOT_STARTED" }}
                    />
                </MemoryRouter>
            )
            const signup = screen.getByTestId("/signup")
            expect(signup).toBeInTheDocument()
        })

        it("should render error page on unexpected error", () => {
            const userData: AsyncOperationResult<void, UserData, Error> = {
                status: "FAILED",
                params: void 0,
                error: new Error("Some error"),
            }
            const fetchUser = jest.fn()
            const clearPasswords = jest.fn()
            render(
                <MemoryRouter>
                    <AuthDecisionContainer
                        userData={userData}
                        fetchUserData={fetchUser}
                        clearPasswords={clearPasswords}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        tReady={true}
                        apiLogoutStatus={{ status: "NOT_STARTED" }}
                    />
                </MemoryRouter>
            )
            const header = screen.getByText("error-pages:user-loading-failed.header")
            const description = screen.getByText("error-pages:user-loading-failed.description")
            expect(header).toBeInTheDocument()
            expect(description).toBeInTheDocument()
        })

        it("should render user routes on success", () => {
            const userData: AsyncOperationResult<void, UserData, Error> = {
                status: "FINISHED",
                params: void 0,
                data: {
                    id: exampleUserId,
                    nickName: exampleUserNickname,
                    language: "Pl",
                    emailHash: exampleHashedEmail,
                },
            }
            const fetchUser = jest.fn()
            const clearPasswords = jest.fn()
            render(
                <MemoryRouter>
                    <AuthDecisionContainer
                        userData={userData}
                        fetchUserData={fetchUser}
                        clearPasswords={clearPasswords}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        tReady={true}
                        apiLogoutStatus={{ status: "NOT_STARTED" }}
                    />
                </MemoryRouter>
            )
            const profile = screen.getByTestId("/profile")
            expect(profile).toBeInTheDocument()
        })

        it("should render loader", () => {
            const userData: AsyncOperationResult<void, UserData, Error> = {
                status: "PENDING",
                params: void 0,
            }
            const fetchUser = jest.fn()
            const clearPasswords = jest.fn()
            render(
                <MemoryRouter>
                    <AuthDecisionContainer
                        userData={userData}
                        fetchUserData={fetchUser}
                        clearPasswords={clearPasswords}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        tReady={true}
                        apiLogoutStatus={{ status: "NOT_STARTED" }}
                    />
                </MemoryRouter>
            )
            const loader = screen.getByTestId(LOADER_PAGE)
            expect(loader).toBeInTheDocument()
        })
    })
})
