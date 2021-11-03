import { render, screen } from "@testing-library/react"
import React from "react"
import { exampleApiKey } from "../../../../testutils/constants/user"
import { i18nMock, tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { ApiKeyList } from "./ApiKeysList"
import * as chakraToast from "@chakra-ui/react"
import { toastMock } from "../../../../testutils/mocks/toast-mock"

// eslint-disable-next-line react/display-name
jest.mock("../../../../application/components/common/EmptyPlaceholder", () => () => (
    <div data-testid="empty-placeholder"></div>
))

// eslint-disable-next-line react/display-name
jest.mock("../../../../application/components/common/responsive_table/ResponsiveTable", () => () => (
    <div data-testid="keys-list"></div>
))

// eslint-disable-next-line react/display-name
jest.mock("../../../../application/components/common/Confirmation", () => () => <div></div>)

// eslint-disable-next-line react/display-name
jest.mock("./NewApiKeyModal", () => () => <div></div>)

describe("Api keys list", () => {
    describe("effects", () => {
        describe("mount and unmount", () => {
            it("should reset update status on mount", () => {
                const resetUpdateStatus = jest.fn()
                render(
                    <ApiKeyList
                        apiKeys={[]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        resetUpdateStatus={resetUpdateStatus}
                        resetCreateStatus={jest.fn()}
                        resetDeleteStatus={jest.fn()}
                        updateResult={{
                            status: "PENDING",
                            params: { data: { description: "new" }, keyId: exampleApiKey.id },
                        }}
                        createResult={{
                            status: "PENDING",
                            params: { description: "new" },
                        }}
                        deleteResult={{
                            status: "PENDING",
                            params: exampleApiKey.id,
                        }}
                        updateKey={jest.fn()}
                        createApiKey={jest.fn()}
                        deleteKey={jest.fn()}
                    />
                )
                expect(resetUpdateStatus).toHaveBeenCalledTimes(1)
            })

            it("should reset create status on mount", () => {
                const resetCreateStatus = jest.fn()
                render(
                    <ApiKeyList
                        apiKeys={[]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        resetUpdateStatus={jest.fn()}
                        resetCreateStatus={resetCreateStatus}
                        resetDeleteStatus={jest.fn()}
                        updateResult={{
                            status: "PENDING",
                            params: { data: { description: "new" }, keyId: exampleApiKey.id },
                        }}
                        createResult={{
                            status: "PENDING",
                            params: { description: "new" },
                        }}
                        deleteResult={{
                            status: "PENDING",
                            params: exampleApiKey.id,
                        }}
                        updateKey={jest.fn()}
                        createApiKey={jest.fn()}
                        deleteKey={jest.fn()}
                    />
                )
                expect(resetCreateStatus).toHaveBeenCalledTimes(1)
            })

            it("should reset delete status on mount", () => {
                const resetDeleteStatus = jest.fn()
                render(
                    <ApiKeyList
                        apiKeys={[]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        resetUpdateStatus={jest.fn()}
                        resetCreateStatus={jest.fn()}
                        resetDeleteStatus={resetDeleteStatus}
                        updateResult={{
                            status: "PENDING",
                            params: { data: { description: "new" }, keyId: exampleApiKey.id },
                        }}
                        createResult={{
                            status: "PENDING",
                            params: { description: "new" },
                        }}
                        deleteResult={{
                            status: "PENDING",
                            params: exampleApiKey.id,
                        }}
                        updateKey={jest.fn()}
                        createApiKey={jest.fn()}
                        deleteKey={jest.fn()}
                    />
                )
                expect(resetDeleteStatus).toHaveBeenCalledTimes(1)
            })

            it("should reset update status on unmount", () => {
                const resetUpdateStatus = jest.fn()
                const element = render(
                    <ApiKeyList
                        apiKeys={[]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        resetUpdateStatus={resetUpdateStatus}
                        resetCreateStatus={jest.fn()}
                        resetDeleteStatus={jest.fn()}
                        updateResult={{
                            status: "PENDING",
                            params: { data: { description: "new" }, keyId: exampleApiKey.id },
                        }}
                        createResult={{
                            status: "PENDING",
                            params: { description: "new" },
                        }}
                        deleteResult={{
                            status: "PENDING",
                            params: exampleApiKey.id,
                        }}
                        updateKey={jest.fn()}
                        createApiKey={jest.fn()}
                        deleteKey={jest.fn()}
                    />
                )
                element.unmount()
                expect(resetUpdateStatus).toHaveBeenCalledTimes(2)
            })

            it("should reset create status on unmount", () => {
                const resetCreateStatus = jest.fn()
                const element = render(
                    <ApiKeyList
                        apiKeys={[]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        resetUpdateStatus={jest.fn()}
                        resetCreateStatus={resetCreateStatus}
                        resetDeleteStatus={jest.fn()}
                        updateResult={{
                            status: "PENDING",
                            params: { data: { description: "new" }, keyId: exampleApiKey.id },
                        }}
                        createResult={{
                            status: "PENDING",
                            params: { description: "new" },
                        }}
                        deleteResult={{
                            status: "PENDING",
                            params: exampleApiKey.id,
                        }}
                        updateKey={jest.fn()}
                        createApiKey={jest.fn()}
                        deleteKey={jest.fn()}
                    />
                )
                element.unmount()
                expect(resetCreateStatus).toHaveBeenCalledTimes(2)
            })

            it("should reset delete status on unmount", () => {
                const resetDeleteStatus = jest.fn()
                const element = render(
                    <ApiKeyList
                        apiKeys={[]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        resetUpdateStatus={jest.fn()}
                        resetCreateStatus={jest.fn()}
                        resetDeleteStatus={resetDeleteStatus}
                        updateResult={{
                            status: "PENDING",
                            params: { data: { description: "new" }, keyId: exampleApiKey.id },
                        }}
                        createResult={{
                            status: "PENDING",
                            params: { description: "new" },
                        }}
                        deleteResult={{
                            status: "PENDING",
                            params: exampleApiKey.id,
                        }}
                        updateKey={jest.fn()}
                        createApiKey={jest.fn()}
                        deleteKey={jest.fn()}
                    />
                )
                element.unmount()
                expect(resetDeleteStatus).toHaveBeenCalledTimes(2)
            })
        })

        describe("action failed", () => {
            it("should show toast and reset status on update failed", () => {
                const toast = jest.fn()
                const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))
                const resetUpdateStatus = jest.fn()
                render(
                    <ApiKeyList
                        apiKeys={[]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        resetUpdateStatus={resetUpdateStatus}
                        resetCreateStatus={jest.fn()}
                        resetDeleteStatus={jest.fn()}
                        updateResult={{
                            status: "FAILED",
                            params: { data: { description: "new" }, keyId: exampleApiKey.id },
                            error: new Error("Some error"),
                        }}
                        createResult={{
                            status: "PENDING",
                            params: { description: "new" },
                        }}
                        deleteResult={{
                            status: "PENDING",
                            params: exampleApiKey.id,
                        }}
                        updateKey={jest.fn()}
                        createApiKey={jest.fn()}
                        deleteKey={jest.fn()}
                    />
                )
                expect(resetUpdateStatus).toHaveBeenCalledTimes(2)
                expect(toast).toHaveBeenCalledTimes(1)
                expect(toast).toHaveBeenCalledWith({ title: "profile-page:api-key-update-failed", status: "warning" })

                useToastMock.mockRestore()
            })

            it("should show toast and reset status on create failed", () => {
                const toast = jest.fn()
                const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))
                const resetCreateStatus = jest.fn()
                render(
                    <ApiKeyList
                        apiKeys={[]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        resetUpdateStatus={jest.fn()}
                        resetCreateStatus={resetCreateStatus}
                        resetDeleteStatus={jest.fn()}
                        updateResult={{
                            status: "PENDING",
                            params: { data: { description: "new" }, keyId: exampleApiKey.id },
                        }}
                        createResult={{
                            status: "FAILED",
                            params: { description: "new" },
                            error: new Error("Some error"),
                        }}
                        deleteResult={{
                            status: "PENDING",
                            params: exampleApiKey.id,
                        }}
                        updateKey={jest.fn()}
                        createApiKey={jest.fn()}
                        deleteKey={jest.fn()}
                    />
                )
                expect(resetCreateStatus).toHaveBeenCalledTimes(2)
                expect(toast).toHaveBeenCalledTimes(1)
                expect(toast).toHaveBeenCalledWith({ title: "profile-page:api-key-create-failed", status: "warning" })

                useToastMock.mockRestore()
            })

            it("should show toast and reset status on delete failed", () => {
                const toast = jest.fn()
                const useToastMock = jest.spyOn(chakraToast, "useToast").mockImplementation(() => toastMock(toast))
                const resetDeleteStatus = jest.fn()
                render(
                    <ApiKeyList
                        apiKeys={[]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        resetUpdateStatus={jest.fn()}
                        resetCreateStatus={jest.fn()}
                        resetDeleteStatus={resetDeleteStatus}
                        updateResult={{
                            status: "PENDING",
                            params: { data: { description: "new" }, keyId: exampleApiKey.id },
                        }}
                        createResult={{
                            status: "PENDING",
                            params: { description: "new" },
                        }}
                        deleteResult={{
                            status: "FAILED",
                            params: exampleApiKey.id,
                            error: new Error("Some error"),
                        }}
                        updateKey={jest.fn()}
                        createApiKey={jest.fn()}
                        deleteKey={jest.fn()}
                    />
                )
                expect(resetDeleteStatus).toHaveBeenCalledTimes(2)
                expect(toast).toHaveBeenCalledTimes(1)
                expect(toast).toHaveBeenCalledWith({ title: "profile-page:api-key-delete-failed", status: "warning" })

                useToastMock.mockRestore()
            })
        })
    })

    describe("render", () => {
        it("should render placeholder if keys list is empty", () => {
            render(
                <ApiKeyList
                    apiKeys={[]}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    resetUpdateStatus={jest.fn()}
                    resetCreateStatus={jest.fn()}
                    resetDeleteStatus={jest.fn()}
                    updateResult={{ status: "NOT_STARTED" }}
                    createResult={{ status: "NOT_STARTED" }}
                    deleteResult={{ status: "NOT_STARTED" }}
                    updateKey={jest.fn()}
                    createApiKey={jest.fn()}
                    deleteKey={jest.fn()}
                />
            )
            screen.getByTestId("empty-placeholder")
        })

        it("should render keys list", () => {
            render(
                <ApiKeyList
                    apiKeys={[exampleApiKey]}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    resetUpdateStatus={jest.fn()}
                    resetCreateStatus={jest.fn()}
                    resetDeleteStatus={jest.fn()}
                    updateResult={{ status: "NOT_STARTED" }}
                    createResult={{ status: "NOT_STARTED" }}
                    deleteResult={{ status: "NOT_STARTED" }}
                    updateKey={jest.fn()}
                    createApiKey={jest.fn()}
                    deleteKey={jest.fn()}
                />
            )
            screen.getByTestId("keys-list")
        })
    })
})
