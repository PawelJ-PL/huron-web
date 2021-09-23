import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import React from "react"
import { exampleFileData } from "../../../../../testutils/constants/files"
import { tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { NEW_FILE_NAME_INPUT, RENAME_FILE_MODAL } from "../../testids"
import { RenameModal } from "./RenameModal"

describe("Rename modal", () => {
    it("should be visible", () => {
        render(
            <RenameModal
                t={tFunctionMock}
                requestedFile={exampleFileData}
                clearRequest={jest.fn()}
                rename={jest.fn()}
            />
        )

        const modal = screen.queryByTestId(RENAME_FILE_MODAL)

        expect(modal).toBeTruthy()
    })

    it("should be hidden", () => {
        render(<RenameModal t={tFunctionMock} requestedFile={null} clearRequest={jest.fn()} rename={jest.fn()} />)

        const modal = screen.queryByTestId(RENAME_FILE_MODAL)

        expect(modal).toBeNull()
    })

    it("should close modal on cancel", () => {
        const clearRequestMock = jest.fn()
        const renameMock = jest.fn()

        render(
            <RenameModal
                t={tFunctionMock}
                requestedFile={exampleFileData}
                clearRequest={clearRequestMock}
                rename={renameMock}
            />
        )

        const cancelButton = screen.getByText("Common:cancel-imperative")

        fireEvent.click(cancelButton)

        expect(clearRequestMock).toHaveBeenCalledTimes(1)
        expect(renameMock).not.toHaveBeenCalled()
    })

    it("should trigger action if name changed", async () => {
        const clearRequestMock = jest.fn()
        const renameMock = jest.fn()

        render(
            <RenameModal
                t={tFunctionMock}
                requestedFile={exampleFileData}
                clearRequest={clearRequestMock}
                rename={renameMock}
            />
        )

        const nameInput = screen.getByTestId(NEW_FILE_NAME_INPUT)

        fireEvent.change(nameInput, { target: { value: "new-name" } })

        const submitButton = screen.getByText("Common:confirm-imperative")

        await waitFor(() => fireEvent.click(submitButton))

        expect(clearRequestMock).toHaveBeenCalledTimes(1)
        expect(renameMock).toHaveBeenCalledTimes(1)
        expect(renameMock).toHaveBeenCalledWith(exampleFileData, "new-name")
    })

    it("should not trigger action if name has not changed", async () => {
        const clearRequestMock = jest.fn()
        const renameMock = jest.fn()

        render(
            <RenameModal
                t={tFunctionMock}
                requestedFile={exampleFileData}
                clearRequest={clearRequestMock}
                rename={renameMock}
            />
        )

        const submitButton = screen.getByText("Common:confirm-imperative")

        await waitFor(() => fireEvent.click(submitButton))

        expect(clearRequestMock).toHaveBeenCalledTimes(1)
        expect(renameMock).not.toHaveBeenCalled()
    })
})
