import { useToast } from "@chakra-ui/react"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { useNavigate, useParams } from "react-router"
import { Dispatch } from "redux"
import ErrorPage from "../../../application/pages/ErrorPage"
import FullScreenLoaderPage from "../../../application/pages/FullScreenLoaderPage"
import { AppState } from "../../../application/store"
import { activateAccountAction, resetActivationStatusAction } from "../store/Actions"

const ACTIVATION_SUCCEED_TOAST_ID = "activation-success"
const ACTIVATION_FAIL_TOAST_ID = "activation-failure"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & WithTranslation

export const AccountActivationContainer: React.FC<Props> = ({ activationResult, activateUser, resetStatus, t }) => {
    const toast = useToast({ isClosable: true })

    const { token = "" } = useParams<{ token: string }>()
    const navigate = useNavigate()

    useEffect(() => {
        activateUser(token)
        return () => {
            resetStatus()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (activationResult.status === "FINISHED") {
            if (activationResult.data) {
                toast({
                    title: t("activation-page:activation-successful-message"),
                    status: "success",
                    id: ACTIVATION_SUCCEED_TOAST_ID,
                })
                navigate("/")
            } else {
                toast({
                    title: t("activation-page:activation-failed-message"),
                    status: "warning",
                    id: ACTIVATION_FAIL_TOAST_ID,
                })
                navigate("/")
            }
        }
    }, [activationResult, t, toast, navigate])

    if (activationResult.status === "FAILED") {
        return (
            <ErrorPage
                title={t("error-pages:user-activation-failed.header")}
                description={t("error-pages:user-activation-failed.description")}
            />
        )
    } else {
        return <FullScreenLoaderPage />
    }
}

const mapStateToProps = (state: AppState) => ({
    activationResult: state.users.accountActivation,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    activateUser: (token: string) => dispatch(activateAccountAction.started(token)),
    resetStatus: () => dispatch(resetActivationStatusAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(AccountActivationContainer))
