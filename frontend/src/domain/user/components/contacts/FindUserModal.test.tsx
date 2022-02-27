import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import React from "react"
import { tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { UserPublicData } from "../../types/UserPublicData"
import { FindUserModal } from "./FindUserModal"
import { FIND_USERS_INPUT } from "./testids"

describe("Find user modal", () => {
    it("should render autocomplete items", () => {
        const matchingUsers: UserPublicData[] = [
            { userId: "1", nickName: "foo" },
            { userId: "2", nickName: "bar" },
            { userId: "3", nickName: "baz" },
        ]

        render(
            <FindUserModal
                isOpen={true}
                onClose={jest.fn()}
                onSelect={jest.fn()}
                t={tFunctionMock}
                matchingUsers={matchingUsers}
                findUsers={jest.fn()}
                resetFindResult={jest.fn()}
            />
        )

        const entry1 = screen.getByText("foo")
        const entry2 = screen.getByText("bar")
        const entry3 = screen.getByText("baz")

        expect(entry1).toBeInTheDocument()
        expect(entry2).toBeInTheDocument()
        expect(entry3).toBeInTheDocument()
    })

    it("should fetch results on input change", async () => {
        const fetchAction = jest.fn()

        render(
            <FindUserModal
                isOpen={true}
                onClose={jest.fn()}
                onSelect={jest.fn()}
                t={tFunctionMock}
                matchingUsers={[]}
                findUsers={fetchAction}
                resetFindResult={jest.fn()}
            />
        )

        const input = screen.getByTestId(FIND_USERS_INPUT)
        fireEvent.change(input, { target: { value: "foobar" } })

        await waitFor(() => expect(fetchAction).toHaveBeenCalledTimes(1))
        expect(fetchAction).toHaveBeenCalledWith("foobar")
    })

    it("should not fetch results on input change if trimmed input is shorter than 5 characters", async () => {
        const fetchAction = jest.fn()

        render(
            <FindUserModal
                isOpen={true}
                onClose={jest.fn()}
                onSelect={jest.fn()}
                t={tFunctionMock}
                matchingUsers={[]}
                findUsers={fetchAction}
                resetFindResult={jest.fn()}
            />
        )

        const input = screen.getByTestId(FIND_USERS_INPUT)
        fireEvent.change(input, { target: { value: "foo" } })

        await expect(waitFor(() => expect(fetchAction).toHaveBeenCalled(), { timeout: 600 })).rejects.toThrow()
    })

    it("should perform action on select value", () => {
        const closeAction = jest.fn()
        const selectAction = jest.fn()

        const matchingUsers: UserPublicData[] = [
            { userId: "1", nickName: "foo" },
            { userId: "2", nickName: "bar" },
            { userId: "3", nickName: "baz" },
        ]

        render(
            <FindUserModal
                isOpen={true}
                onClose={closeAction}
                onSelect={selectAction}
                t={tFunctionMock}
                matchingUsers={matchingUsers}
                findUsers={jest.fn()}
                resetFindResult={jest.fn()}
            />
        )

        const entry2 = screen.getByText("bar")
        fireEvent.click(entry2)

        expect(closeAction).toHaveBeenCalledTimes(1)
        expect(selectAction).toHaveBeenCalledTimes(1)
        expect(selectAction).toHaveBeenCalledWith({ userId: "2", nickName: "bar" })
    })
})
