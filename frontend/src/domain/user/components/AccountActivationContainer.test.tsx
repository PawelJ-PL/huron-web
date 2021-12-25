import { screen } from "@testing-library/react"
import React from "react"
import { LOADER_PAGE } from "../../../application/pages/testids"
import { i18nMock, tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { AccountActivationContainer } from "./AccountActivationContainer"
import * as chakraToast from "@chakra-ui/toast"
import { toastMock } from "../../../testutils/mocks/toast-mock"
import { renderWithRoute } from "../../../testutils/helpers"

const startPath = "/account-activation/X-Y-Z"
const pathTemplate = "/account-activation/:token"

// eslint-disable-next-line testing-library/render-result-naming-convention
const renderWithPath = renderWithRoute(pathTemplate)

describe("Account activation container", () => {
    beforeEach(() => window.history.replaceState({}, "", startPath))

    describe("mount and unmount", () => {
        it("should send activation request on mount", () => {
            const activateFn = jest.fn()
            renderWithPath(
                <AccountActivationContainer
                    activationResult={{ status: "NOT_STARTED" }}
                    activateUser={activateFn}
                    resetStatus={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            expect(activateFn).toHaveBeenCalledTimes(1)
            expect(activateFn).toHaveBeenCalledWith("X-Y-Z")
        })

        it("should reset status on unmount", () => {
            const resetFn = jest.fn()
            const view = renderWithPath(
                <AccountActivationContainer
                    activationResult={{ status: "NOT_STARTED" }}
                    activateUser={jest.fn()}
                    resetStatus={resetFn}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            view.unmount()
            expect(resetFn).toHaveBeenCalledTimes(1)
        })
    })

    describe("render decision", () => {
        it("should render loader", () => {
            renderWithPath(
                <AccountActivationContainer
                    activationResult={{ status: "PENDING", params: "X-Y-Z" }}
                    activateUser={jest.fn()}
                    resetStatus={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            screen.getByTestId(LOADER_PAGE)
        })

        it("should render error page on failure", () => {
            renderWithPath(
                <AccountActivationContainer
                    activationResult={{ status: "FAILED", params: "X-Y-Z", error: new Error("Some error") }}
                    activateUser={jest.fn()}
                    resetStatus={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            screen.getByText("error-pages:user-activation-failed.header")
            screen.getByText("error-pages:user-activation-failed.description")
        })

        it("should navigate to home and show toast on valid token", () => {
            const toast = jest.fn()
            const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))
            renderWithPath(
                <AccountActivationContainer
                    activationResult={{ status: "FINISHED", params: "X-Y-Z", data: true }}
                    activateUser={jest.fn()}
                    resetStatus={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            expect(window.location.pathname).toBe("/")
            expect(toast).toHaveBeenCalledTimes(1)
            expect(toast).toHaveBeenCalledWith({
                id: "activation-success",
                status: "success",
                title: "activation-page:activation-successful-message",
            })
            useToastMock.mockRestore()
        })

        it("should navigate to home and show toast on invalid token", () => {
            const toast = jest.fn()
            const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))
            renderWithPath(
                <AccountActivationContainer
                    activationResult={{ status: "FINISHED", params: "X-Y-Z", data: false }}
                    activateUser={jest.fn()}
                    resetStatus={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            expect(window.location.pathname).toBe("/")
            expect(toast).toHaveBeenCalledTimes(1)
            expect(toast).toHaveBeenCalledWith({
                id: "activation-failure",
                status: "warning",
                title: "activation-page:activation-failed-message",
            })
            useToastMock.mockRestore()
        })
    })
})
