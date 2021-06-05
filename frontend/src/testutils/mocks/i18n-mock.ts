import { i18n } from "i18next"

export function i18nMock(properties?: { languages?: string[]; changeLanguage?: () => void }): i18n {
    return {
        languages: properties?.languages ?? ["pl", "en"],
        changeLanguage: properties?.changeLanguage ?? (() => void 0),
    } as i18n
}

export const tFunctionMock: (t: string) => string = (text: string) => text
