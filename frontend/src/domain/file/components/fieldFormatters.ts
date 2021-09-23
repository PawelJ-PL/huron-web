import { i18n } from "i18next"
import { dateLocaleForLang } from "../../../application/localization/utils"
import format from "date-fns/format"
import prettyBytes from "pretty-bytes"

export function formatDate(i18n: i18n, dateString: string): string {
    const dateLocale = dateLocaleForLang(i18n.languages[0])
    const date = new Date(dateString)
    return format(date, "Pp", { locale: dateLocale })
}

export function formatFileSize(i18n: i18n, bytes: number): string {
    const locale = i18n.languages[0].toLowerCase()
    return prettyBytes(bytes, { locale, maximumFractionDigits: 2 })
}
