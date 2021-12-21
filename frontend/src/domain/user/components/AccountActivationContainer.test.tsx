import { render, screen } from "@testing-library/react"
import React from "react"
import { LOADER_PAGE } from "../../../application/pages/testids"
import { i18nMock, tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { historyMock } from "../../../testutils/mocks/router-mock"
import { AccountActivationContainer } from "./AccountActivationContainer"
import * as chakraToast from "@chakra-ui/toast"
import { toastMock } from "../../../testutils/mocks/toast-mock"

describe("Account activation container", () => {
    describe("mount and unmount", () => {
        it("should send activation request on mount", () => {
            const activateFn = jest.fn()
            render(
                <AccountActivationContainer
                    history={historyMock()}
                    match={{ isExact: true, url: "/", path: "/", params: { token: "X-Y-Z" } }}
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
            const view = render(
                <AccountActivationContainer
                    history={historyMock()}
                    match={{ isExact: true, url: "/", path: "/", params: { token: "X-Y-Z" } }}
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
            render(
                <AccountActivationContainer
                    history={historyMock()}
                    match={{ isExact: true, url: "/", path: "/", params: { token: "X-Y-Z" } }}
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
            render(
                <AccountActivationContainer
                    history={historyMock()}
                    match={{ isExact: true, url: "/", path: "/", params: { token: "X-Y-Z" } }}
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
            const historyReplace = jest.fn()
            const toast = jest.fn()
            const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))
            render(
                <AccountActivationContainer
                    history={historyMock({ replace: historyReplace })}
                    match={{ isExact: true, url: "/", path: "/", params: { token: "X-Y-Z" } }}
                    activationResult={{ status: "FINISHED", params: "X-Y-Z", data: true }}
                    activateUser={jest.fn()}
                    resetStatus={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            expect(historyReplace).toHaveBeenCalledTimes(1)
            expect(historyReplace).toHaveBeenCalledWith("/")
            expect(toast).toHaveBeenCalledTimes(1)
            expect(toast).toHaveBeenCalledWith({
                id: "activation-success",
                status: "success",
                title: "activation-page:activation-successful-message",
            })
            useToastMock.mockRestore()
        })

        it("should navigate to home and show toast on invalid token", () => {
            const historyReplace = jest.fn()
            const toast = jest.fn()
            const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))
            render(
                <AccountActivationContainer
                    history={historyMock({ replace: historyReplace })}
                    match={{ isExact: true, url: "/", path: "/", params: { token: "X-Y-Z" } }}
                    activationResult={{ status: "FINISHED", params: "X-Y-Z", data: false }}
                    activateUser={jest.fn()}
                    resetStatus={jest.fn()}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    tReady={true}
                />
            )
            expect(historyReplace).toHaveBeenCalledTimes(1)
            expect(historyReplace).toHaveBeenCalledWith("/")
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
