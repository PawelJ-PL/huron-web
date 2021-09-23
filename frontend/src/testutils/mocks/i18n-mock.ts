import { i18n, TFunction } from "i18next"

export function i18nMock(properties?: { languages?: string[]; changeLanguage?: () => Promise<TFunction> }): i18n {
    return {
        languages: properties?.languages ?? (["pl", "en"] as readonly string[]),
        changeLanguage: properties?.changeLanguage ?? (() => Promise.resolve(tFunctionMock)),
    } as i18n
}

export const tFunctionMock: (t: string) => string = (text: string) => text
