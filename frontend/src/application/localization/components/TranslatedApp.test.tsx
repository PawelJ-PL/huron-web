import { render } from "@testing-library/react"
import React from "react"
import { i18nMock, tFunctionMock } from "../../../testutils/mocks/i18n-mock"
import { TranslatedApp } from "./TranslatedApp"

describe("Translated app", () => {
    it("should change app language when profile language changed", () => {
        const changeLanguage = jest.fn()
        const i18n = i18nMock({ changeLanguage })
        const element = render(<TranslatedApp t={tFunctionMock} i18n={i18n} tReady={true} language="pl" />)
        element.rerender(<TranslatedApp t={tFunctionMock} i18n={i18n} tReady={true} language="en" />)
        expect(changeLanguage).toHaveBeenCalledTimes(1)
        expect(changeLanguage).toHaveBeenCalledWith("en")
    })

    it("should do nothing if new language not provided", () => {
        const changeLanguage = jest.fn()
        const i18n = i18nMock({ changeLanguage })
        const element = render(<TranslatedApp t={tFunctionMock} i18n={i18n} tReady={true} language="pl" />)
        element.rerender(<TranslatedApp t={tFunctionMock} i18n={i18n} tReady={true} language={undefined} />)
        expect(changeLanguage).not.toHaveBeenCalled()
    })

    it("should do nothing if new language is not supported", () => {
        const changeLanguage = jest.fn()
        const i18n = i18nMock({ changeLanguage })
        const element = render(<TranslatedApp t={tFunctionMock} i18n={i18n} tReady={true} language="pl" />)
        element.rerender(<TranslatedApp t={tFunctionMock} i18n={i18n} tReady={true} language="foo" />)
        expect(changeLanguage).not.toHaveBeenCalled()
    })

    it("should do nothing if new language is the same than previous one", () => {
        const changeLanguage = jest.fn()
        const i18n = i18nMock({ changeLanguage })
        const element = render(<TranslatedApp t={tFunctionMock} i18n={i18n} tReady={true} language="pl" />)
        element.rerender(<TranslatedApp t={tFunctionMock} i18n={i18n} tReady={true} language="pl" />)
        expect(changeLanguage).not.toHaveBeenCalled()
    })
})
