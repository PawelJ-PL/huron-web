import i18n from "i18next"
import LanguageDetector from "i18next-browser-languagedetector"
import { initReactI18next } from "react-i18next"
import en from "./translations/en-US.json"
import pl from "./translations/pl-PL.json"
import plLocale from "date-fns/locale/pl"
import enLocale from "date-fns/locale/en-US"
import { registerLocale } from "react-datepicker"

export const supportedLanguages = ["en", "pl"]

registerLocale("en", enLocale)
registerLocale("pl", plLocale)

const tPromise = i18n
    .use(initReactI18next)
    .use(LanguageDetector)
    .init({
        resources: { en, pl },
        fallbackLng: "en",
        supportedLngs: supportedLanguages,
        returnNull: false,
        returnEmptyString: false,
        interpolation: { escapeValue: false },
        detection: {
            order: ["cookie", "navigator"],
            lookupCookie: "language",
            caches: ["cookie"],
            cookieOptions: {
                path: "/",
                secure: true,
                maxAge: 604800,
            },
        },
        debug: false,
        nonExplicitSupportedLngs: true,
    })

export default tPromise
