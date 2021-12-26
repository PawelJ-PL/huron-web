import React, { useEffect } from "react"
import { useTranslation, WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { useNavigate } from "react-router-dom"
import { Dispatch } from "redux"
import LoginScreen from "../domain/user/components/LoginScreen"
import { clearMasterKeyAction, fetchCurrentUserAction, resetLoginResultAction } from "../domain/user/store/Actions"
import { NotLoggedIn } from "./api/ApiError"
import ErrorPage from "./pages/ErrorPage"
import FullScreenLoaderPage from "./pages/FullScreenLoaderPage"
import AppRouter from "./router/AppRouter"
import { guestRoutes } from "./router/GuestRoutes"
import { userRoutes } from "./router/UserRoutes"
import { AppState } from "./store"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & WithTranslation

export const AuthDecisionContainer: React.FC<Props> = ({
    fetchUserData,
    userData,
    t,
    clearPasswords,
    apiLogoutStatus,
}) => {
    const navigate = useNavigate()

    useEffect(() => {
        fetchUserData()
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (userData.status === "FAILED" && userData.error instanceof NotLoggedIn) {
            clearPasswords()
        }
    }, [userData, clearPasswords])

    useEffect(() => {
        if (apiLogoutStatus.status === "FINISHED") {
            navigate("/")
        }
    })

    if (userData.status === "FAILED" && userData.error instanceof NotLoggedIn) {
        return <AppRouter routes={guestRoutes} defaultComponent={LoginScreen} />
    } else if (userData.status === "FAILED") {
        return (
            <ErrorPage
                title={t("error-pages:user-loading-failed.header")}
                description={t("error-pages:user-loading-failed.description")}
            />
        )
    }
    if (userData.status === "FINISHED") {
        return <AppRouter routes={userRoutes} defaultComponent={PageNotFoundComponent} />
    } else {
        return <FullScreenLoaderPage />
    }
}

const PageNotFoundComponent: React.FC = () => {
    const { t } = useTranslation()

    return <ErrorPage title="404" description={t("error-pages:page-not-found.description")} />
}

const mapStateToProps = (state: AppState) => ({
    userData: state.users.userData,
    apiLogoutStatus: state.users.logoutStatus,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchUserData: () => dispatch(fetchCurrentUserAction.started()),
    clearPasswords: () => {
        dispatch(resetLoginResultAction())
        dispatch(clearMasterKeyAction())
    },
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(AuthDecisionContainer))
