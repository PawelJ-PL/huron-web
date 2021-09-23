import { render, screen } from "@testing-library/react"
import React from "react"
import { tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { ApiKeysView } from "./ApiKeysView"

// eslint-disable-next-line react/display-name
jest.mock("./ApiKeysList", () => () => <div data-testid="keys-list"></div>)

jest.mock("../../../../application/components/common/UnexpectedErrorMessage")

describe("Api keys view", () => {
    describe("mount and unmount", () => {
        it("should fetch keys on mount", () => {
            const fetchKeys = jest.fn()
            render(
                <ApiKeysView
                    apiKeys={{ status: "FINISHED", params: void 0, data: [] }}
                    t={tFunctionMock}
                    fetchKeys={fetchKeys}
                    resetKeysResult={jest.fn()}
                />
            )
            expect(fetchKeys).toHaveBeenCalledTimes(1)
        })

        it("should not fetch keys on mount if current status is PENDING", () => {
            const fetchKeys = jest.fn()
            render(
                <ApiKeysView
                    apiKeys={{ status: "PENDING", params: void 0 }}
                    t={tFunctionMock}
                    fetchKeys={fetchKeys}
                    resetKeysResult={jest.fn()}
                />
            )
            expect(fetchKeys).not.toHaveBeenLastCalledWith()
        })

        it("should reset result on unmount", () => {
            const resetResult = jest.fn()
            const element = render(
                <ApiKeysView
                    apiKeys={{ status: "PENDING", params: void 0 }}
                    t={tFunctionMock}
                    fetchKeys={jest.fn()}
                    resetKeysResult={resetResult}
                />
            )
            element.unmount()
            expect(resetResult).toHaveBeenCalledTimes(1)
        })
    })

    describe("render", () => {
        it("should return loader if status is NOT_STARTED", () => {
            render(
                <ApiKeysView
                    apiKeys={{ status: "NOT_STARTED" }}
                    t={tFunctionMock}
                    fetchKeys={jest.fn()}
                    resetKeysResult={jest.fn()}
                />
            )
            screen.getByText("profile-page:loading-api-keys")
        })

        it("should return loader if status is PENDING", () => {
            render(
                <ApiKeysView
                    apiKeys={{ status: "PENDING", params: void 0 }}
                    t={tFunctionMock}
                    fetchKeys={jest.fn()}
                    resetKeysResult={jest.fn()}
                />
            )
            screen.getByText("profile-page:loading-api-keys")
        })

        it("should return error if status is FAILED", () => {
            render(
                <ApiKeysView
                    apiKeys={{ status: "FAILED", params: void 0, error: new Error("Some error") }}
                    t={tFunctionMock}
                    fetchKeys={jest.fn()}
                    resetKeysResult={jest.fn()}
                />
            )
            const errorMessage = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorMessage.textContent).toEqual("Some error")
        })

        it("should return list if status is FINISHED", () => {
            render(
                <ApiKeysView
                    apiKeys={{ status: "FINISHED", params: void 0, data: [] }}
                    t={tFunctionMock}
                    fetchKeys={jest.fn()}
                    resetKeysResult={jest.fn()}
                />
            )
            screen.getByTestId("keys-list")
        })
    })
})
