import { render, screen } from "@testing-library/react"
import React from "react"
import { UserData } from "../domain/user/types/UserData"
import { exampleUserId, exampleUserNickname } from "../testutils/constants/user"
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
                <AuthDecisionContainer
                    userData={userData}
                    fetchUserData={fetchUser}
                    clearPasswords={clearPasswords}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
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
                <AuthDecisionContainer
                    userData={userData}
                    fetchUserData={fetchUser}
                    clearPasswords={clearPasswords}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            expect(clearPasswords).toHaveBeenCalledTimes(1)
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
                <AuthDecisionContainer
                    userData={userData}
                    fetchUserData={fetchUser}
                    clearPasswords={clearPasswords}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            screen.getByTestId("/signup")
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
                <AuthDecisionContainer
                    userData={userData}
                    fetchUserData={fetchUser}
                    clearPasswords={clearPasswords}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            screen.getByText("error-pages:user-loading-failed.header")
            screen.getByText("error-pages:user-loading-failed.description")
        })

        it("should render user routes on success", () => {
            const userData: AsyncOperationResult<void, UserData, Error> = {
                status: "FINISHED",
                params: void 0,
                data: { id: exampleUserId, nickName: exampleUserNickname, language: "Pl" },
            }
            const fetchUser = jest.fn()
            const clearPasswords = jest.fn()
            render(
                <AuthDecisionContainer
                    userData={userData}
                    fetchUserData={fetchUser}
                    clearPasswords={clearPasswords}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            screen.getByTestId("/profile")
        })

        it("should render loader", () => {
            const userData: AsyncOperationResult<void, UserData, Error> = {
                status: "PENDING",
                params: void 0,
            }
            const fetchUser = jest.fn()
            const clearPasswords = jest.fn()
            render(
                <AuthDecisionContainer
                    userData={userData}
                    fetchUserData={fetchUser}
                    clearPasswords={clearPasswords}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            screen.getByTestId(LOADER_PAGE)
        })
    })
})
