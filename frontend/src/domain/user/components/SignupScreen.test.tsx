import { screen } from "@testing-library/react"
import React from "react"
import { exampleUserEmail, exampleUserNickname, exampleUserPassword } from "../../../testutils/constants/user"
import { i18nMock, tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { toastMock } from "../../../testutils/mocks/toast-mock"
import { SignupScreen } from "./SignupScreen"
import * as chakraToast from "@chakra-ui/toast"
import { EmailAlreadyRegistered, NicknameAlreadyRegistered } from "../api/errors"
import { renderWithRoute } from "../../../testutils/helpers"

// eslint-disable-next-line react/display-name
jest.mock("./SignupForm", () => () => <div></div>)
jest.mock("../../../application/components/common/UnexpectedErrorMessage")

jest.mock("react-i18next", () => ({
    ...jest.requireActual("react-i18next"),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any, react/display-name, testing-library/no-node-access
    Trans: (props: any) => <div>{props?.children}</div>,
}))

const startPath = "/signup"

// eslint-disable-next-line testing-library/render-result-naming-convention
const renderWithPath = renderWithRoute(startPath)

describe("Signup screen", () => {
    beforeEach(() => window.history.replaceState({}, "", startPath))

    it("should clear status on unmount", () => {
        const resetResult = jest.fn()
        const view = renderWithPath(
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
            />
        )
        view.unmount()
        expect(resetResult).toHaveBeenCalledTimes(1)
    })

    it("should show toast and redirect to home on success", () => {
        const toast = jest.fn()
        const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))
        renderWithPath(
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
            />
        )

        expect(toast).toHaveBeenCalledTimes(1)
        expect(toast).toHaveBeenCalledWith({ title: "signup-page:signup-success-message", id: "signup-success" })
        expect(window.location.pathname).toBe("/")

        useToastMock.mockRestore()
    })

    it("should render proper message if email already registered", () => {
        renderWithPath(
            <SignupScreen
                signupResult={{
                    status: "FAILED",
                    params: {
                        nickname: exampleUserNickname,
                        email: exampleUserEmail,
                        password: exampleUserPassword,
                    },
                    error: new EmailAlreadyRegistered(exampleUserEmail),
                }}
                signup={jest.fn()}
                resetResult={jest.fn()}
                t={tFunctionMock}
                i18n={i18nMock()}
                tReady={true}
            />
        )

        screen.getByText("signup-page:user-already-registered")
    })

    it("should render proper message if nickname already registered", () => {
        renderWithPath(
            <SignupScreen
                signupResult={{
                    status: "FAILED",
                    params: {
                        nickname: exampleUserNickname,
                        email: exampleUserEmail,
                        password: exampleUserPassword,
                    },
                    error: new NicknameAlreadyRegistered(exampleUserNickname),
                }}
                signup={jest.fn()}
                resetResult={jest.fn()}
                t={tFunctionMock}
                i18n={i18nMock()}
                tReady={true}
            />
        )

        screen.getByText("signup-page:nickname-already-registered")
    })
})
