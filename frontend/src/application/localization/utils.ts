import plLocale from "date-fns/locale/pl"
import enLocale from "date-fns/locale/en-US"

export const dateLocaleForLang: (l: string) => Locale = (lang: string) => {
    switch (lang) {
        case "pl":
            return plLocale
        case "en":
            return enLocale
        default:
            return enLocale
    }
}
