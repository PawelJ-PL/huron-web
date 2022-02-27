import React from "react"
import { exampleUserEmail, exampleUserPassword } from "../../../testutils/constants/user"
import { tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { ResetPasswordPage } from "./ResetPasswordPage"
import * as chakraToast from "@chakra-ui/toast"
import { toastMock } from "../../../testutils/mocks/toast-mock"
import { renderWithRoute } from "../../../testutils/helpers"

// eslint-disable-next-line react/display-name
jest.mock("./ResetPasswordForm", () => () => <div></div>)
jest.mock("../../../application/components/common/UnexpectedErrorMessage")

const startRoute = "/set-password/X-Y-Z"
const pathTemplate = "/set-password/:token"

const renderWithPath = renderWithRoute(pathTemplate)

describe("Reset password page", () => {
    beforeEach(() => window.history.replaceState({}, "", startRoute))

    it("should reset status on unmount", () => {
        const clearResult = jest.fn()
        const view = renderWithPath(
            <ResetPasswordPage
                t={tFunctionMock}
                actionResult={{
                    status: "FAILED",
                    params: { email: exampleUserEmail, newPassword: exampleUserPassword, resetToken: "X-Y-Z" },
                    error: new Error("Some error"),
                }}
                resetPassword={jest.fn()}
                clearActionResult={clearResult}
            />
        )
        view.unmount()
        expect(clearResult).toHaveBeenCalledTimes(1)
    })

    it("should redirect to home page and show toast on success", () => {
        const toast = jest.fn()
        const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))

        renderWithPath(
            <ResetPasswordPage
                t={tFunctionMock}
                actionResult={{
                    status: "FINISHED",
                    params: { email: exampleUserEmail, newPassword: exampleUserPassword, resetToken: "X-Y-Z" },
                    data: true,
                }}
                resetPassword={jest.fn()}
                clearActionResult={jest.fn()}
            />
        )
        expect(window.location.pathname).toBe("/")
        expect(toast).toHaveBeenCalledTimes(1)
        expect(toast).toHaveBeenCalledWith({
            title: "reset-password-page:password-has-been-reset",
            id: "password-reset-success",
            status: "success",
        })

        useToastMock.mockRestore()
    })
})
