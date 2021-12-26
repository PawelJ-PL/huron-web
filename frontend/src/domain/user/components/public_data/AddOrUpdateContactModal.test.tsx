import React from "react"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { exampleUserId, exampleUserNickname } from "../../../../testutils/constants/user"
import { tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { AddOrUpdateContactModal } from "./AddOrUpdateContactModal"

jest.mock("react-i18next", () => ({
    withTranslation: () => (Component: React.ComponentClass | React.FC) => {
        Component.defaultProps = { ...Component.defaultProps, t: (k: string) => k }
        return Component
    },
}))

describe("Add or update contact modal", () => {
    describe("add contact", () => {
        it("should create contact without alias when checkbox is unchecked", () => {
            const onClose = jest.fn()
            const createContact = jest.fn()
            render(
                <AddOrUpdateContactModal
                    contactData={{ userId: exampleUserId }}
                    isOpen={true}
                    onClose={onClose}
                    createContact={createContact}
                    editContact={jest.fn()}
                    t={tFunctionMock}
                />
            )
            const maybeAliasInput = screen.queryByLabelText("Common:alias")
            expect(maybeAliasInput).toBeNull()
            const submitButton = screen.getByText("Common:confirm-imperative")
            fireEvent.click(submitButton)
            expect(createContact).toHaveBeenCalledTimes(1)
            expect(createContact).toHaveBeenCalledWith(exampleUserId, undefined)
            expect(onClose).toHaveBeenCalledTimes(1)
        })

        it("should create contact with alias when checkbox is checked", async () => {
            const onClose = jest.fn()
            const createContact = jest.fn()
            render(
                <AddOrUpdateContactModal
                    contactData={{ userId: exampleUserId }}
                    isOpen={true}
                    onClose={onClose}
                    createContact={createContact}
                    editContact={jest.fn()}
                    t={tFunctionMock}
                />
            )
            const checkBox = screen.getByLabelText("user-public-page:add-or-edit-contact-modal.enable-alias")
            fireEvent.click(checkBox)
            const aliasInput = await screen.findByLabelText("Common:alias")
            fireEvent.change(aliasInput, { target: { value: "newAlias" } })
            const submitButton = screen.getByText("Common:confirm-imperative")
            fireEvent.click(submitButton)
            await waitFor(() => expect(createContact).toHaveBeenCalledTimes(1))
            expect(createContact).toHaveBeenCalledWith(exampleUserId, "newAlias")
            expect(onClose).toHaveBeenCalledTimes(1)
        })
    })

    describe("edit contact", () => {
        it("should edit contact without alias when checkbox is unchecked", () => {
            const onClose = jest.fn()
            const editContact = jest.fn()
            render(
                <AddOrUpdateContactModal
                    contactData={{ userId: exampleUserId, nickName: exampleUserNickname, alias: null }}
                    isOpen={true}
                    onClose={onClose}
                    createContact={jest.fn()}
                    editContact={editContact}
                    t={tFunctionMock}
                />
            )
            const maybeAliasInput = screen.queryByLabelText("Common:alias")
            expect(maybeAliasInput).toBeNull()
            const submitButton = screen.getByText("Common:confirm-imperative")
            fireEvent.click(submitButton)
            expect(editContact).toHaveBeenCalledTimes(1)
            expect(editContact).toHaveBeenCalledWith(exampleUserId, undefined)
            expect(onClose).toHaveBeenCalledTimes(1)
        })

        it("should edit contact with alias when checkbox is checked", async () => {
            const onClose = jest.fn()
            const editContact = jest.fn()
            render(
                <AddOrUpdateContactModal
                    contactData={{ userId: exampleUserId, nickName: exampleUserNickname, alias: null }}
                    isOpen={true}
                    onClose={onClose}
                    createContact={jest.fn()}
                    editContact={editContact}
                    t={tFunctionMock}
                />
            )
            const checkBox = screen.getByLabelText("user-public-page:add-or-edit-contact-modal.enable-alias")
            fireEvent.click(checkBox)
            const aliasInput = await screen.findByLabelText("Common:alias")
            fireEvent.change(aliasInput, { target: { value: "newAlias" } })
            const submitButton = screen.getByText("Common:confirm-imperative")
            fireEvent.click(submitButton)
            await waitFor(() => expect(editContact).toHaveBeenCalledTimes(1))
            expect(editContact).toHaveBeenCalledWith(exampleUserId, "newAlias")
            expect(onClose).toHaveBeenCalledTimes(1)
        })

        it("should set default alias if provided", () => {
            const onClose = jest.fn()
            render(
                <AddOrUpdateContactModal
                    contactData={{ userId: exampleUserId, nickName: exampleUserNickname, alias: exampleUserNickname }}
                    isOpen={true}
                    onClose={onClose}
                    createContact={jest.fn()}
                    editContact={jest.fn()}
                    t={tFunctionMock}
                />
            )
            const aliasInput = screen.getByLabelText("Common:alias") as HTMLInputElement
            expect(aliasInput.value).toBe(exampleUserNickname)
        })
    })
})
