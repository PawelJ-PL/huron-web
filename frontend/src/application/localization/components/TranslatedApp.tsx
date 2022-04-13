import React, { Suspense, useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { AppState } from "../../store"
import { supportedLanguages } from "../i18n"

type Props = { children?: React.ReactNode } & WithTranslation & ReturnType<typeof mapStateToProps>

export const TranslatedApp: React.FC<Props> = ({ children, i18n, language }) => {
    useEffect(() => {
        if (language && supportedLanguages.includes(language) && language !== i18n.languages[0]) {
            void i18n.changeLanguage(language)
        }
    }, [language, i18n])

    return <Suspense fallback={<div></div>}>{children}</Suspense>
}

const mapStateToProps = (state: AppState) => ({
    language: state.users.userData.status === "FINISHED" ? state.users.userData.data.language.toLowerCase() : undefined,
})

export default connect(mapStateToProps)(withTranslation()(TranslatedApp))
