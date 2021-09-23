import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import React from "react"
import { exampleCollectionId } from "../../../../../testutils/constants/collection"
import { exampleDirectoryData } from "../../../../../testutils/constants/files"
import { tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { NEW_DIRECTORY_NAME_INPUT } from "../../testids"
import { NewDirectoryModal } from "./NewDirectoryModal"

describe("New directory modal", () => {
    it("should trigger action on submit", async () => {
        const onCloseMock = jest.fn()

        const createDirMock = jest.fn()

        render(
            <NewDirectoryModal
                isOpen={true}
                onClose={onCloseMock}
                parent={exampleDirectoryData.id}
                collectionId={exampleCollectionId}
                t={tFunctionMock}
                createDirectory={createDirMock}
            />
        )

        const nameInput = await screen.findByTestId(NEW_DIRECTORY_NAME_INPUT)

        fireEvent.change(nameInput, { target: { value: "some-name" } })

        const confirmButton = await screen.findByText("Common:create-imperative")
        await waitFor(() => fireEvent.click(confirmButton))

        expect(createDirMock).toHaveBeenCalledTimes(1)
        expect(createDirMock).toHaveBeenCalledWith({
            collectionId: exampleCollectionId,
            parent: exampleDirectoryData.id,
            name: "some-name",
        })
        expect(onCloseMock).toHaveBeenCalledTimes(1)
    })

    it("should close modal on cancel", async () => {
        const onCloseMock = jest.fn()

        const createDirMock = jest.fn()

        render(
            <NewDirectoryModal
                isOpen={true}
                onClose={onCloseMock}
                parent={exampleDirectoryData.id}
                collectionId={exampleCollectionId}
                t={tFunctionMock}
                createDirectory={createDirMock}
            />
        )

        const cancelButton = await screen.findByText("Common:cancel-imperative")
        fireEvent.click(cancelButton)

        expect(createDirMock).not.toHaveBeenCalled()
        expect(onCloseMock).toHaveBeenCalledTimes(1)
    })
})
