import { render, screen } from "@testing-library/react"
import React from "react"
import { Pagination } from "../../../../application/api/Pagination"
import { exampleUserId, exampleUserNickname } from "../../../../testutils/constants/user"
import { tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { UserContact } from "../../types/UserContact"
import { ContactsContainer } from "./ContactsContainer"

// eslint-disable-next-line react/display-name
jest.mock("./ContactsPage", () => (props: { contacts: Pagination<UserContact[]>; appliedFilter?: string }) => (
    <div data-testid="CONTACTS_PAGE_MOCK">
        <div>CONTACTS: {JSON.stringify(props.contacts)}</div>
        <div>APPLIED_FILTER: {JSON.stringify(props.appliedFilter)}</div>
    </div>
))

jest.mock("../../../../application/components/common/UnexpectedErrorMessage")

jest.mock("../../../../application/components/common/Loader")

describe("mount and unmount", () => {
    it("should reset contacts filter on mount", () => {
        const resetFilter = jest.fn()

        render(
            <ContactsContainer
                contactsListResult={{ status: "NOT_STARTED" }}
                fetchContacts={jest.fn()}
                resetContactsFilter={resetFilter}
                resetContactsListResult={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(resetFilter).toHaveBeenCalledTimes(1)
    })

    it("should reset contacts filter on unmount", () => {
        const resetFilter = jest.fn()

        const view = render(
            <ContactsContainer
                contactsListResult={{ status: "NOT_STARTED" }}
                fetchContacts={jest.fn()}
                resetContactsFilter={resetFilter}
                resetContactsListResult={jest.fn()}
                t={tFunctionMock}
            />
        )

        view.unmount()

        expect(resetFilter).toHaveBeenCalledTimes(2)
    })

    it("should fetch contacts on mount", () => {
        const fetchContacts = jest.fn()

        render(
            <ContactsContainer
                contactsListResult={{ status: "NOT_STARTED" }}
                fetchContacts={fetchContacts}
                resetContactsFilter={jest.fn()}
                resetContactsListResult={jest.fn()}
                t={tFunctionMock}
            />
        )

        expect(fetchContacts).toHaveBeenCalledTimes(1)
        expect(fetchContacts).toHaveBeenCalledWith({})
    })

    it("should reset contacts on unmount", () => {
        const resetContacts = jest.fn()

        const view = render(
            <ContactsContainer
                contactsListResult={{ status: "NOT_STARTED" }}
                fetchContacts={jest.fn()}
                resetContactsFilter={jest.fn()}
                resetContactsListResult={resetContacts}
                t={tFunctionMock}
            />
        )

        expect(resetContacts).not.toHaveBeenCalled()

        view.unmount()

        expect(resetContacts).toHaveBeenCalledTimes(1)
    })

    describe("render", () => {
        it("should render contacts page", () => {
            const result = {
                page: 1,
                elementsPerPage: 30,
                totalPages: 1,
                result: [{ userId: exampleUserId, nickName: exampleUserNickname, alias: "alias-1" }],
            }

            render(
                <ContactsContainer
                    contactsListResult={{
                        status: "FINISHED",
                        params: { nameFilter: "foo" },
                        data: result,
                    }}
                    fetchContacts={jest.fn()}
                    resetContactsFilter={jest.fn()}
                    resetContactsListResult={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const contactsPage = screen.getByTestId("CONTACTS_PAGE_MOCK")
            expect(contactsPage.textContent).toMatch(`CONTACTS: ${JSON.stringify(result)}`)
            expect(contactsPage.textContent).toMatch('FILTER: "foo"')
        })

        it("should render unexpected error", () => {
            render(
                <ContactsContainer
                    contactsListResult={{
                        status: "FAILED",
                        params: { nameFilter: "foo" },
                        error: new Error("Some error"),
                    }}
                    fetchContacts={jest.fn()}
                    resetContactsFilter={jest.fn()}
                    resetContactsListResult={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const errorMessage = screen.getByTestId("UNEXPECTED_ERROR_MESSAGE_MOCK")
            expect(errorMessage.textContent).toBe("Some error")
        })

        it("should render loader", () => {
            render(
                <ContactsContainer
                    contactsListResult={{
                        status: "PENDING",
                        params: { nameFilter: "foo" },
                    }}
                    fetchContacts={jest.fn()}
                    resetContactsFilter={jest.fn()}
                    resetContactsListResult={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const loader = screen.getByTestId("LOADER_MOCK")
            expect(loader.textContent).toMatch("contacts-list:loader-text")
        })
    })
})
