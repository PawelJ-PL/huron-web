import React from "react"
import { tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { RequestPasswordResetPage } from "./RequestPasswordResetPage"
import * as chakraToast from "@chakra-ui/toast"
import { toastMock } from "../../../testutils/mocks/toast-mock"
import { renderWithRoute } from "../../../testutils/helpers"

// eslint-disable-next-line react/display-name
jest.mock("./RequestPasswordResetForm", () => () => <div></div>)

jest.mock("../../../application/components/common/UnexpectedErrorMessage")

jest.mock("react-i18next", () => ({
    ...jest.requireActual("react-i18next"),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    Trans: (props: any) => <div>{props?.children}</div>,
}))

const path = "/reset-password"

const renderWithPath = renderWithRoute(path)

describe("Request password reset page", () => {
    beforeEach(() => window.history.replaceState({}, "", path))

    describe("mount and unmount", () => {
        it("should clear status on unmount", () => {
            const clearStatus = jest.fn()
            const view = renderWithPath(
                <RequestPasswordResetPage
                    t={tFunctionMock}
                    actionResult={{ status: "FAILED", params: "X-Y-Z", error: new Error("some error") }}
                    sendResetPasswordRequest={jest.fn()}
                    clearStatus={clearStatus}
                />
            )
            view.unmount()
            expect(clearStatus).toHaveBeenCalledTimes(1)
        })
    })

    describe("redirect", () => {
        it("should show toast and redirect to home on success", () => {
            const toast = jest.fn()
            const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))
            renderWithPath(
                <RequestPasswordResetPage
                    t={tFunctionMock}
                    actionResult={{ status: "FINISHED", params: "X-Y-Z", data: void 0 }}
                    sendResetPasswordRequest={jest.fn()}
                    clearStatus={jest.fn()}
                />
            )
            expect(window.location.pathname).toBe("/")
            expect(toast).toHaveBeenCalledTimes(1)
            expect(toast).toHaveBeenCalledWith({
                title: "password-reset-request-page:request-success-message",
                id: "request-success",
            })

            useToastMock.mockRestore()
        })
    })
})
