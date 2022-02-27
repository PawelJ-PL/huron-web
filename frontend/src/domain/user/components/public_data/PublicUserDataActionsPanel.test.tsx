import React from "react"
import { screen } from "@testing-library/react"
import { exampleUserPublicData } from "../../../../testutils/constants/user"
import { PublicUserDataActionsPanel } from "./PublicUserDataActionsPanel"
import { tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { renderWithRoute } from "../../../../testutils/helpers"

// eslint-disable-next-line react/display-name
jest.mock("./AddOrUpdateContactModal", () => () => <div></div>)

// eslint-disable-next-line react/display-name
jest.mock("../../../../application/components/common/Confirmation", () => () => <div></div>)

const pathTemplate = "/"

const renderWithPath = renderWithRoute(pathTemplate)

describe("Public user data actions panel", () => {
    describe("action buttons", () => {
        describe("add contact button", () => {
            it("should be visible", () => {
                renderWithPath(
                    <PublicUserDataActionsPanel
                        userPublicData={{ ...exampleUserPublicData, contactData: null }}
                        self={false}
                        t={tFunctionMock}
                        removeFromContacts={jest.fn()}
                    />
                )
                const button = screen.getByText("user-public-page:save-contact-button")
                expect(button).toBeInTheDocument()
            })

            it("should not be visible for self profile", () => {
                renderWithPath(
                    <PublicUserDataActionsPanel
                        userPublicData={{ ...exampleUserPublicData, contactData: null }}
                        self={true}
                        t={tFunctionMock}
                        removeFromContacts={jest.fn()}
                    />
                )
                const button = screen.queryByText("user-public-page:save-contact-button")
                expect(button).not.toBeInTheDocument()
            })

            it("should not be visible if user data includes contact", () => {
                renderWithPath(
                    <PublicUserDataActionsPanel
                        userPublicData={exampleUserPublicData}
                        self={false}
                        t={tFunctionMock}
                        removeFromContacts={jest.fn()}
                    />
                )
                const button = screen.queryByText("user-public-page:save-contact-button")
                expect(button).not.toBeInTheDocument()
            })
        })

        describe("remove contact button", () => {
            it("should be visible", () => {
                renderWithPath(
                    <PublicUserDataActionsPanel
                        userPublicData={exampleUserPublicData}
                        self={false}
                        t={tFunctionMock}
                        removeFromContacts={jest.fn()}
                    />
                )
                const button = screen.getByText("user-public-page:remove-contact-button")
                expect(button).toBeInTheDocument()
            })

            it("should not be visible for self profile", () => {
                renderWithPath(
                    <PublicUserDataActionsPanel
                        userPublicData={exampleUserPublicData}
                        self={true}
                        t={tFunctionMock}
                        removeFromContacts={jest.fn()}
                    />
                )
                const button = screen.queryByText("user-public-page:remove-contact-button")
                expect(button).not.toBeInTheDocument()
            })

            it("should not be visible if user data does not include contact", () => {
                renderWithPath(
                    <PublicUserDataActionsPanel
                        userPublicData={{ ...exampleUserPublicData, contactData: null }}
                        self={false}
                        t={tFunctionMock}
                        removeFromContacts={jest.fn()}
                    />
                )
                const button = screen.queryByText("user-public-page:remove-contact-button")
                expect(button).not.toBeInTheDocument()
            })
        })

        describe("self profile button", () => {
            it("should be visible", () => {
                renderWithPath(
                    <PublicUserDataActionsPanel
                        userPublicData={{ ...exampleUserPublicData, contactData: null }}
                        self={true}
                        t={tFunctionMock}
                        removeFromContacts={jest.fn()}
                    />
                )
                const button = screen.getByText("user-public-page:go-to-profile-button")
                expect(button).toBeInTheDocument()
            })

            it("should not be visible for not self profile", () => {
                renderWithPath(
                    <PublicUserDataActionsPanel
                        userPublicData={{ ...exampleUserPublicData, contactData: null }}
                        self={false}
                        t={tFunctionMock}
                        removeFromContacts={jest.fn()}
                    />
                )
                const button = screen.queryByText("user-public-page:go-to-profile-button")
                expect(button).not.toBeInTheDocument()
            })
        })

        describe("edit profile button", () => {
            it("should be visible", () => {
                renderWithPath(
                    <PublicUserDataActionsPanel
                        userPublicData={exampleUserPublicData}
                        self={false}
                        t={tFunctionMock}
                        removeFromContacts={jest.fn()}
                    />
                )
                const button = screen.getByText("user-public-page:edit-contact-button")
                expect(button).toBeInTheDocument()
            })

            it("should not be visible for self profile", () => {
                renderWithPath(
                    <PublicUserDataActionsPanel
                        userPublicData={exampleUserPublicData}
                        self={true}
                        t={tFunctionMock}
                        removeFromContacts={jest.fn()}
                    />
                )
                const button = screen.queryByText("user-public-page:edit-contact-button")
                expect(button).not.toBeInTheDocument()
            })

            it("should not be visible if user data does not include contact", () => {
                renderWithPath(
                    <PublicUserDataActionsPanel
                        userPublicData={{ ...exampleUserPublicData, contactData: null }}
                        self={false}
                        t={tFunctionMock}
                        removeFromContacts={jest.fn()}
                    />
                )
                const button = screen.queryByText("user-public-page:edit-contact-button")
                expect(button).not.toBeInTheDocument()
            })
        })
    })
})
