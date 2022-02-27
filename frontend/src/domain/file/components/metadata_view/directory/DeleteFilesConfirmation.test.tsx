import { fireEvent, render, screen } from "@testing-library/react"
import React from "react"
import { exampleCollectionId } from "../../../../../testutils/constants/collection"
import { exampleChildFile1, exampleDirectoryData, exampleFileData } from "../../../../../testutils/constants/files"
import { tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { DELETE_FILE_CONFIRMATION_MODAL } from "../../testids"
import { DeleteFilesConfirmation } from "./DeleteFilesConfirmation"

describe("Delete files confirmation", () => {
    describe("Open modal", () => {
        it("should be open", () => {
            render(
                <DeleteFilesConfirmation
                    requestedFiles={[exampleFileData, exampleDirectoryData]}
                    clearRequest={jest.fn()}
                    deleteFiles={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const modal = screen.queryByTestId(DELETE_FILE_CONFIRMATION_MODAL)
            expect(modal).toBeTruthy()
        })

        it("should be closed if no file was requested", () => {
            render(
                <DeleteFilesConfirmation
                    requestedFiles={null}
                    clearRequest={jest.fn()}
                    deleteFiles={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const modal = screen.queryByTestId(DELETE_FILE_CONFIRMATION_MODAL)
            expect(modal).toBeNull()
        })

        it("should be closed if requested array is empty", () => {
            render(
                <DeleteFilesConfirmation
                    requestedFiles={[]}
                    clearRequest={jest.fn()}
                    deleteFiles={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const modal = screen.queryByTestId(DELETE_FILE_CONFIRMATION_MODAL)
            expect(modal).toBeNull()
        })

        it("should be closed if requested array contains elements from different collections", () => {
            render(
                <DeleteFilesConfirmation
                    requestedFiles={[exampleFileData, { ...exampleDirectoryData, collectionId: "other-collection" }]}
                    clearRequest={jest.fn()}
                    deleteFiles={jest.fn()}
                    t={tFunctionMock}
                />
            )

            const modal = screen.queryByTestId(DELETE_FILE_CONFIRMATION_MODAL)
            expect(modal).toBeNull()
        })
    })

    it("should reset requested files on exit", () => {
        const clearRequestMock = jest.fn()

        render(
            <DeleteFilesConfirmation
                requestedFiles={[exampleFileData, exampleDirectoryData]}
                clearRequest={clearRequestMock}
                deleteFiles={jest.fn()}
                t={tFunctionMock}
            />
        )

        const cancelButton = screen.getByText("Common:cancel-imperative")

        fireEvent.click(cancelButton)

        expect(clearRequestMock).toHaveBeenCalledTimes(1)
    })

    describe("delete files", () => {
        it("should delete files on confirmation click", () => {
            const deleteMock = jest.fn()

            render(
                <DeleteFilesConfirmation
                    requestedFiles={[exampleFileData, exampleDirectoryData]}
                    clearRequest={jest.fn()}
                    deleteFiles={deleteMock}
                    t={tFunctionMock}
                />
            )

            const confirmationButton = screen.getByText("Common:confirm-imperative")

            fireEvent.click(confirmationButton)

            expect(deleteMock).toHaveBeenCalledTimes(1)
            expect(deleteMock).toHaveBeenCalledWith(
                exampleCollectionId,
                [exampleFileData.id, exampleDirectoryData.id],
                false
            )
        })

        it("should delete files recursively if checkbox selected", () => {
            const deleteMock = jest.fn()

            render(
                <DeleteFilesConfirmation
                    requestedFiles={[exampleFileData, exampleDirectoryData]}
                    clearRequest={jest.fn()}
                    deleteFiles={deleteMock}
                    t={tFunctionMock}
                />
            )

            const recursivelyCheckBox = screen.getByText("file-view:directory-content-list.delete-recursively")

            fireEvent.click(recursivelyCheckBox)

            const confirmationButton = screen.getByText("Common:confirm-imperative")

            fireEvent.click(confirmationButton)

            expect(deleteMock).toHaveBeenCalledTimes(1)
            expect(deleteMock).toHaveBeenCalledWith(
                exampleCollectionId,
                [exampleFileData.id, exampleDirectoryData.id],
                true
            )
        })
    })

    it("should hide recursively checkbox if request contains only files", () => {
        render(
            <DeleteFilesConfirmation
                requestedFiles={[exampleFileData, exampleChildFile1]}
                clearRequest={jest.fn()}
                deleteFiles={jest.fn()}
                t={tFunctionMock}
            />
        )

        const recursivelyCheckBox = screen.queryByText("file-view:directory-content-list.delete-recursively")

        expect(recursivelyCheckBox).toBeNull()
    })
})
