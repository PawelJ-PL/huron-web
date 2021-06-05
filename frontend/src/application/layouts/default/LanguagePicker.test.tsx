import React from "react"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { LanguagePicker } from "./LanguagePicker"
import { i18nMock } from "../../../testutils/mocks/i18n-mock"
import { LANGUAGE_MENU_ITEM_PREFIX, LANGUAGE_SELECTION } from "./testids"

jest.mock("@chakra-ui/media-query", () => ({
    ...jest.requireActual("@chakra-ui/media-query"),
    useBreakpointValue: () => true,
}))

describe("Language picker", () => {
    it("should change current language", async () => {
        const updateLang = jest.fn()
        const changeLanguage = jest.fn()
        const i18n = i18nMock({ changeLanguage })
        render(<LanguagePicker i18n={i18n} updateProfileLanguage={updateLang} />)
        const langSelection = screen.getByTestId(LANGUAGE_SELECTION)
        fireEvent.click(langSelection)
        const englishMenuEntry = await waitFor(() => screen.getByTestId(LANGUAGE_MENU_ITEM_PREFIX + "_ENGLISH"))
        fireEvent.click(englishMenuEntry)
        expect(changeLanguage).toHaveBeenLastCalledWith("en")
        expect(updateLang).toHaveBeenCalledWith("en")
    })

    it("should do nothing if current language selected", async () => {
        const updateLang = jest.fn()
        const changeLanguage = jest.fn()
        const i18n = i18nMock({ changeLanguage })
        render(<LanguagePicker i18n={i18n} updateProfileLanguage={updateLang} />)
        const langSelection = screen.getByTestId(LANGUAGE_SELECTION)
        fireEvent.click(langSelection)
        const englishMenuEntry = await waitFor(() => screen.getByTestId(LANGUAGE_MENU_ITEM_PREFIX + "_POLSKI"))
        fireEvent.click(englishMenuEntry)
        expect(changeLanguage).not.toHaveBeenCalled()
        expect(updateLang).not.toHaveBeenCalled()
    })
})
