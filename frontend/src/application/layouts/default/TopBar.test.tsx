import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import React from "react"
import { MemoryRouter } from "react-router"
import { exampleUserId, exampleUserNickname } from "../../../testutils/constants/user"
import { tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { historyMock } from "../../../testutils/mocks/router-mock"
import { TopBar } from "./TopBar"

jest.mock("@chakra-ui/media-query", () => ({
    ...jest.requireActual("@chakra-ui/media-query"),
    useBreakpointValue: () => true,
}))

// eslint-disable-next-line react/display-name
jest.mock("./LanguagePicker", () => () => <div></div>)

const userData = { nickName: exampleUserNickname, id: exampleUserId, language: "Pl" }

describe("Top bar", () => {
    describe("logout", () => {
        it("should trigger logout", async () => {
            const logoutFn = jest.fn()
            const clearStatus = jest.fn()
            render(
                <MemoryRouter>
                    <TopBar
                        t={tFunctionMock}
                        history={historyMock()}
                        userData={userData}
                        logoutStatus="NOT_STARTED"
                        logout={logoutFn}
                        clearLogoutStatus={clearStatus}
                    />
                </MemoryRouter>
            )
            const profile = screen.getByText(exampleUserNickname)
            fireEvent.click(profile)
            const logoutItem = await waitFor(() => screen.getByText("top-bar:account-menu-items.logout"))
            fireEvent.click(logoutItem)
            expect(logoutFn).toHaveBeenCalledTimes(1)
        })

        it("should show toast and reset status", async () => {
            const logoutFn = jest.fn()
            const clearStatus = jest.fn()
            render(
                <MemoryRouter>
                    <TopBar
                        t={tFunctionMock}
                        history={historyMock()}
                        userData={userData}
                        logoutStatus="FAILED"
                        logout={logoutFn}
                        clearLogoutStatus={clearStatus}
                    />
                </MemoryRouter>
            )
            await waitFor(() => screen.getByText("top-bar:logout-failed-message"))
            expect(clearStatus).toHaveBeenCalledTimes(2)
        })

        it("should reset logout status on unmount", () => {
            const logoutFn = jest.fn()
            const clearStatus = jest.fn()
            const element = render(
                <MemoryRouter>
                    <TopBar
                        t={tFunctionMock}
                        history={historyMock()}
                        userData={userData}
                        logoutStatus="NOT_STARTED"
                        logout={logoutFn}
                        clearLogoutStatus={clearStatus}
                    />
                </MemoryRouter>
            )
            element.unmount()
            expect(clearStatus).toHaveBeenCalledTimes(2)
        })
    })
})