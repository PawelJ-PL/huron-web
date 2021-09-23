import { fireEvent, render, screen } from "@testing-library/react"
import React from "react"
import { exampleVersion } from "../../../../../testutils/constants/files"
import { tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { DeleteVersionModal } from "./DeleteVersionModal"

describe("Delete version modal", () => {
    it("should be hidden if no version was requested to delete", () => {
        render(
            <DeleteVersionModal
                requestedToDelete={null}
                clearRequest={jest.fn()}
                deleteVersion={jest.fn()}
                t={tFunctionMock}
            />
        )
        const modal = screen.queryByText("file-view:file-data.delete-version-confirmation")
        expect(modal).toBeNull()
    })

    it("should be visible if version was requested to delete", () => {
        render(
            <DeleteVersionModal
                requestedToDelete={exampleVersion}
                clearRequest={jest.fn()}
                deleteVersion={jest.fn()}
                t={tFunctionMock}
            />
        )

        screen.getByText("file-view:file-data.delete-version-confirmation")
    })

    it("should clear request on cancel", () => {
        const clearRequestMock = jest.fn()
        const deleteVersionMock = jest.fn()

        render(
            <DeleteVersionModal
                requestedToDelete={exampleVersion}
                clearRequest={clearRequestMock}
                deleteVersion={deleteVersionMock}
                t={tFunctionMock}
            />
        )

        const cancelButton = screen.getByText("Common:cancel-imperative")
        fireEvent.click(cancelButton)

        expect(clearRequestMock).toHaveBeenCalledTimes(1)
        expect(deleteVersionMock).not.toHaveBeenCalled()
    })

    it("should trigger action on confirm", () => {
        const clearRequestMock = jest.fn()
        const deleteVersionMock = jest.fn()

        render(
            <DeleteVersionModal
                requestedToDelete={exampleVersion}
                clearRequest={clearRequestMock}
                deleteVersion={deleteVersionMock}
                t={tFunctionMock}
            />
        )

        const confirmButton = screen.getByText("Common:confirm-imperative")
        fireEvent.click(confirmButton)

        expect(clearRequestMock).toHaveBeenCalledTimes(1)
        expect(deleteVersionMock).toHaveBeenCalledTimes(1)
        expect(deleteVersionMock).toHaveBeenCalledWith(exampleVersion)
    })
})
