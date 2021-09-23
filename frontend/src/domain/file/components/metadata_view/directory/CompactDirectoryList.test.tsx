import { fireEvent, render, screen } from "@testing-library/react"
import React from "react"
import { MemoryRouter } from "react-router-dom"
import { exampleCollectionId } from "../../../../../testutils/constants/collection"
import {
    exampleChildDirectory1,
    exampleChildFile1,
    exampleDirectoryData,
    exampleFileData,
} from "../../../../../testutils/constants/files"
import { i18nMock } from "../../../../../testutils/mocks/i18n-mock"
import { SELECT_FILE_CHECKBOX } from "../../testids"
import { CompactDirectoryList } from "./CompactDirectoryList"

// eslint-disable-next-line react/display-name
jest.mock("./ObjectActionsMenu", () => () => <div data-testid="OBJECT_ACTIONS_MENU_MOCK"></div>)

describe("Compact directory list", () => {
    describe("mount and unmount", () => {
        it("should reset selected files", () => {
            const setSelectedFilesMock = jest.fn()

            const elem = render(
                <MemoryRouter>
                    <CompactDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleFileData,
                            exampleChildFile1,
                            exampleChildDirectory1,
                        ]}
                        collectionId={exampleCollectionId}
                        i18n={i18nMock()}
                        selectedFiles={[exampleFileData, exampleDirectoryData]}
                        setSelectedFiles={setSelectedFilesMock}
                    />
                </MemoryRouter>
            )

            elem.unmount()

            expect(setSelectedFilesMock).toHaveBeenCalledTimes(2)
            expect(setSelectedFilesMock).toHaveBeenNthCalledWith(1, [])
            expect(setSelectedFilesMock).toHaveBeenNthCalledWith(2, [])
        })
    })

    describe("file selection", () => {
        it("should select specified files", () => {
            render(
                <MemoryRouter>
                    <CompactDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleFileData,
                            exampleChildFile1,
                            exampleChildDirectory1,
                        ]}
                        collectionId={exampleCollectionId}
                        i18n={i18nMock()}
                        selectedFiles={[exampleFileData, exampleDirectoryData]}
                        setSelectedFiles={jest.fn()}
                    />
                </MemoryRouter>
            )

            const file1CheckBox = screen.getByTestId(`${SELECT_FILE_CHECKBOX}_${exampleDirectoryData.id}`)
            const file2CheckBox = screen.getByTestId(`${SELECT_FILE_CHECKBOX}_${exampleFileData.id}`)
            const file3CheckBox = screen.getByTestId(`${SELECT_FILE_CHECKBOX}_${exampleChildFile1.id}`)
            const file4CheckBox = screen.getByTestId(`${SELECT_FILE_CHECKBOX}_${exampleChildDirectory1.id}`)

            expect(file1CheckBox.firstChild).toBeChecked()
            expect(file2CheckBox.firstChild).toBeChecked()
            expect(file3CheckBox.firstChild).not.toBeChecked()
            expect(file4CheckBox.firstChild).not.toBeChecked()
        })

        it("should remove selection on click", () => {
            const setSelectionMock = jest.fn()

            render(
                <MemoryRouter>
                    <CompactDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleFileData,
                            exampleChildFile1,
                            exampleChildDirectory1,
                        ]}
                        collectionId={exampleCollectionId}
                        i18n={i18nMock()}
                        selectedFiles={[exampleDirectoryData, exampleFileData, exampleChildFile1]}
                        setSelectedFiles={setSelectionMock}
                    />
                </MemoryRouter>
            )

            const fileCheckBox = screen.getByTestId(`${SELECT_FILE_CHECKBOX}_${exampleFileData.id}`)

            fireEvent.click(fileCheckBox)

            expect(setSelectionMock).toHaveBeenCalledTimes(2)
            expect(setSelectionMock).toHaveBeenNthCalledWith(1, [])
            expect(setSelectionMock).toHaveBeenNthCalledWith(2, [exampleDirectoryData, exampleChildFile1])
        })

        it("should add selection on click", () => {
            const setSelectionMock = jest.fn()

            render(
                <MemoryRouter>
                    <CompactDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleFileData,
                            exampleChildFile1,
                            exampleChildDirectory1,
                        ]}
                        collectionId={exampleCollectionId}
                        i18n={i18nMock()}
                        selectedFiles={[exampleDirectoryData, exampleChildFile1]}
                        setSelectedFiles={setSelectionMock}
                    />
                </MemoryRouter>
            )

            const fileCheckBox = screen.getByTestId(`${SELECT_FILE_CHECKBOX}_${exampleFileData.id}`)

            fireEvent.click(fileCheckBox)

            expect(setSelectionMock).toHaveBeenCalledTimes(2)
            expect(setSelectionMock).toHaveBeenNthCalledWith(1, [])
            expect(setSelectionMock).toHaveBeenNthCalledWith(2, [
                exampleDirectoryData,
                exampleChildFile1,
                exampleFileData,
            ])
        })
    })
})
