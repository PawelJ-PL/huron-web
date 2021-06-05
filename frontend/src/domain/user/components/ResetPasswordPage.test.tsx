import { render } from "@testing-library/react"
import React from "react"
import { exampleUserEmail, exampleUserPassword } from "../../../testutils/constants/user"
import { tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { historyMock } from "../../../testutils/mocks/router-mock"
import { ResetPasswordPage } from "./ResetPasswordPage"
import * as chakraToast from "@chakra-ui/react"
import { toastMock } from "../../../testutils/mocks/toast-mock"

// eslint-disable-next-line react/display-name
jest.mock("./ResetPasswordForm", () => () => <div></div>)

describe("Reset password page", () => {
    it("should reset status on unmount", () => {
        const clearResult = jest.fn()
        const element = render(
            <ResetPasswordPage
                t={tFunctionMock}
                actionResult={{
                    status: "FAILED",
                    params: { email: exampleUserEmail, newPassword: exampleUserPassword, resetToken: "X-Y-Z" },
                    error: new Error("Some error"),
                }}
                resetPassword={jest.fn()}
                clearActionResult={clearResult}
                history={historyMock()}
                match={{ isExact: true, path: "/", url: "/", params: { token: "X-Y-Z" } }}
            />
        )
        element.unmount()
        expect(clearResult).toHaveBeenCalledTimes(1)
    })

    it("should redirect to home page and show toast on success", () => {
        const toast = jest.fn()
        const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))
        const historyReplace = jest.fn()

        render(
            <ResetPasswordPage
                t={tFunctionMock}
                actionResult={{
                    status: "FINISHED",
                    params: { email: exampleUserEmail, newPassword: exampleUserPassword, resetToken: "X-Y-Z" },
                    data: true,
                }}
                resetPassword={jest.fn()}
                clearActionResult={jest.fn()}
                history={historyMock({ replace: historyReplace })}
                match={{ isExact: true, path: "/", url: "/", params: { token: "X-Y-Z" } }}
            />
        )
        expect(historyReplace).toHaveBeenCalledTimes(1)
        expect(historyReplace).toHaveBeenCalledWith("/")
        expect(toast).toHaveBeenCalledTimes(1)
        expect(toast).toHaveBeenCalledWith({
            title: "reset-password-page:password-has-been-reset",
            id: "password-reset-success",
            status: "success",
        })

        useToastMock.mockRestore()
    })
})
