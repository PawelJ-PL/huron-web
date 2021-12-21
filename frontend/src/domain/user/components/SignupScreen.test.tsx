import { render } from "@testing-library/react"
import React from "react"
import { MemoryRouter } from "react-router"
import { exampleUserEmail, exampleUserNickname, exampleUserPassword } from "../../../testutils/constants/user"
import { i18nMock, tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { historyMock } from "../../../testutils/mocks/router-mock"
import { toastMock } from "../../../testutils/mocks/toast-mock"
import { SignupScreen } from "./SignupScreen"
import * as chakraToast from "@chakra-ui/toast"

// eslint-disable-next-line react/display-name
jest.mock("./SignupForm", () => () => <div></div>)
jest.mock("../../../application/components/common/UnexpectedErrorMessage")

jest.mock("react-i18next", () => ({
    ...jest.requireActual("react-i18next"),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any, react/display-name, testing-library/no-node-access
    Trans: (props: any) => <div>{props?.children}</div>,
}))

describe("Signup screen", () => {
    it("should clear status on unmount", () => {
        const resetResult = jest.fn()
        const view = render(
            <MemoryRouter>
                <SignupScreen
                    signupResult={{
                        status: "FAILED",
                        params: {
                            nickname: exampleUserNickname,
                            email: exampleUserEmail,
                            password: exampleUserPassword,
                        },
                        error: new Error("Some error"),
                    }}
                    signup={jest.fn()}
                    resetResult={resetResult}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                    history={historyMock()}
                />
            </MemoryRouter>
        )
        view.unmount()
        expect(resetResult).toHaveBeenCalledTimes(1)
    })

    it("should show toast and redirect to home on success", () => {
        const toast = jest.fn()
        const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))
        const historyPush = jest.fn()
        render(
            <MemoryRouter>
                <SignupScreen
                    signupResult={{
                        status: "FINISHED",
                        params: {
                            nickname: exampleUserNickname,
                            email: exampleUserEmail,
                            password: exampleUserPassword,
                        },
                        data: void 0,
                    }}
                    signup={jest.fn()}
                    resetResult={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                    history={historyMock({ push: historyPush })}
                />
            </MemoryRouter>
        )

        expect(toast).toHaveBeenCalledTimes(1)
        expect(toast).toHaveBeenCalledWith({ title: "signup-page:signup-success-message", id: "signup-success" })
        expect(historyPush).toHaveBeenCalledTimes(1)
        expect(historyPush).toHaveBeenCalledWith("/")

        useToastMock.mockRestore()
    })
})
