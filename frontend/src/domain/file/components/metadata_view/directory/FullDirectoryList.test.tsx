import { fireEvent, render, screen } from "@testing-library/react"
import React from "react"
import { MemoryRouter } from "react-router"
import { exampleCollectionId } from "../../../../../testutils/constants/collection"
import {
    exampleChildDirectory1,
    exampleChildFile1,
    exampleDirectoryData,
    exampleFileData,
} from "../../../../../testutils/constants/files"
import { i18nMock, tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { SELECT_ALL_FILES_CHECKBOX, SELECT_FILE_CHECKBOX } from "../../testids"
import { FullDirectoryList } from "./FullDirectoryList"

// eslint-disable-next-line react/display-name
jest.mock("./ObjectActionsMenu", () => () => <div data-testid="OBJECT_ACTIONS_MENU_MOCK"></div>)

// eslint-disable-next-line react/display-name
jest.mock("../file/Author", () => () => <div data-testid="AUTHOR_MOCK"></div>)

describe("Full directory list", () => {
    it("should rest files selection on mount and unmount", () => {
        const setSelectedMock = jest.fn()

        const view = render(
            <MemoryRouter>
                <FullDirectoryList
                    childObjects={[exampleDirectoryData, exampleChildDirectory1, exampleFileData, exampleChildFile1]}
                    t={tFunctionMock}
                    i18n={i18nMock()}
                    collectionId={exampleCollectionId}
                    selectedFiles={[exampleChildDirectory1, exampleChildFile1]}
                    setSelectedFiles={setSelectedMock}
                />
            </MemoryRouter>
        )

        view.unmount()

        expect(setSelectedMock).toHaveBeenCalledTimes(2)
        expect(setSelectedMock).toHaveBeenNthCalledWith(1, [])
        expect(setSelectedMock).toHaveBeenNthCalledWith(2, [])
    })

    describe("select all checkbox", () => {
        it("should be checked if all files are selected", () => {
            render(
                <MemoryRouter>
                    <FullDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleChildDirectory1,
                            exampleFileData,
                            exampleChildFile1,
                        ]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        collectionId={exampleCollectionId}
                        selectedFiles={[
                            exampleDirectoryData,
                            exampleChildDirectory1,
                            exampleFileData,
                            exampleChildFile1,
                        ]}
                        setSelectedFiles={jest.fn()}
                    />
                </MemoryRouter>
            )

            const checkBox = screen.getByTestId(SELECT_ALL_FILES_CHECKBOX)

            // eslint-disable-next-line testing-library/no-node-access
            expect(checkBox.firstChild).toBeChecked()
            // eslint-disable-next-line testing-library/no-node-access
            expect(checkBox.firstChild).not.toBePartiallyChecked()
        })

        it("should be partially checked if some files are selected", () => {
            render(
                <MemoryRouter>
                    <FullDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleChildDirectory1,
                            exampleFileData,
                            exampleChildFile1,
                        ]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        collectionId={exampleCollectionId}
                        selectedFiles={[exampleDirectoryData, exampleFileData]}
                        setSelectedFiles={jest.fn()}
                    />
                </MemoryRouter>
            )

            const checkBox = screen.getByTestId(SELECT_ALL_FILES_CHECKBOX)

            // eslint-disable-next-line testing-library/no-node-access
            expect(checkBox.firstChild).not.toBeChecked()
            // eslint-disable-next-line testing-library/no-node-access
            expect(checkBox.firstChild).toBePartiallyChecked()
        })

        it("should not be checked if no file is selected", () => {
            render(
                <MemoryRouter>
                    <FullDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleChildDirectory1,
                            exampleFileData,
                            exampleChildFile1,
                        ]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        collectionId={exampleCollectionId}
                        selectedFiles={[]}
                        setSelectedFiles={jest.fn()}
                    />
                </MemoryRouter>
            )

            const checkBox = screen.getByTestId(SELECT_ALL_FILES_CHECKBOX)

            // eslint-disable-next-line testing-library/no-node-access
            expect(checkBox.firstChild).not.toBeChecked()
            // eslint-disable-next-line testing-library/no-node-access
            expect(checkBox.firstChild).not.toBePartiallyChecked()
        })

        it("should select all on click", () => {
            const setSelectedMock = jest.fn()

            render(
                <MemoryRouter>
                    <FullDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleChildDirectory1,
                            exampleFileData,
                            exampleChildFile1,
                        ]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        collectionId={exampleCollectionId}
                        selectedFiles={[exampleChildDirectory1]}
                        setSelectedFiles={setSelectedMock}
                    />
                </MemoryRouter>
            )

            const checkBox = screen.getByTestId(SELECT_ALL_FILES_CHECKBOX)

            fireEvent.click(checkBox)

            expect(setSelectedMock).toHaveBeenCalledTimes(2)
            expect(setSelectedMock).toHaveBeenNthCalledWith(1, [])
            expect(setSelectedMock).toHaveBeenNthCalledWith(2, [
                exampleDirectoryData,
                exampleChildDirectory1,
                exampleFileData,
                exampleChildFile1,
            ])
        })

        it("should deselect all on click", () => {
            const setSelectedMock = jest.fn()

            render(
                <MemoryRouter>
                    <FullDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleChildDirectory1,
                            exampleFileData,
                            exampleChildFile1,
                        ]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        collectionId={exampleCollectionId}
                        selectedFiles={[
                            exampleDirectoryData,
                            exampleChildDirectory1,
                            exampleFileData,
                            exampleChildFile1,
                        ]}
                        setSelectedFiles={setSelectedMock}
                    />
                </MemoryRouter>
            )

            const checkBox = screen.getByTestId(SELECT_ALL_FILES_CHECKBOX)

            fireEvent.click(checkBox)

            expect(setSelectedMock).toHaveBeenCalledTimes(2)
            expect(setSelectedMock).toHaveBeenNthCalledWith(1, [])
            expect(setSelectedMock).toHaveBeenNthCalledWith(2, [])
        })
    })

    describe("file selection", () => {
        it("should select specified files", () => {
            render(
                <MemoryRouter>
                    <FullDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleChildDirectory1,
                            exampleFileData,
                            exampleChildFile1,
                        ]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        collectionId={exampleCollectionId}
                        selectedFiles={[exampleDirectoryData, exampleChildFile1]}
                        setSelectedFiles={jest.fn()}
                    />
                </MemoryRouter>
            )

            const file1CheckBox = screen.getByTestId(`${SELECT_FILE_CHECKBOX}_${exampleDirectoryData.id}`)
            const file2CheckBox = screen.getByTestId(`${SELECT_FILE_CHECKBOX}_${exampleChildDirectory1.id}`)
            const file3CheckBox = screen.getByTestId(`${SELECT_FILE_CHECKBOX}_${exampleFileData.id}`)
            const file4CheckBox = screen.getByTestId(`${SELECT_FILE_CHECKBOX}_${exampleChildFile1.id}`)

            // eslint-disable-next-line testing-library/no-node-access
            expect(file1CheckBox.firstChild).toBeChecked()
            // eslint-disable-next-line testing-library/no-node-access
            expect(file2CheckBox.firstChild).not.toBeChecked()
            // eslint-disable-next-line testing-library/no-node-access
            expect(file3CheckBox.firstChild).not.toBeChecked()
            // eslint-disable-next-line testing-library/no-node-access
            expect(file4CheckBox.firstChild).toBeChecked()
        })

        it("should remove selection on click", () => {
            const setSelectionMock = jest.fn()

            render(
                <MemoryRouter>
                    <FullDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleChildDirectory1,
                            exampleFileData,
                            exampleChildFile1,
                        ]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        collectionId={exampleCollectionId}
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
                    <FullDirectoryList
                        childObjects={[
                            exampleDirectoryData,
                            exampleChildDirectory1,
                            exampleFileData,
                            exampleChildFile1,
                        ]}
                        t={tFunctionMock}
                        i18n={i18nMock()}
                        collectionId={exampleCollectionId}
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
