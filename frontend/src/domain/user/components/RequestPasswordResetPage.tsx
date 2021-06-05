import { Box, Link, Text } from "@chakra-ui/layout"
import { useToast } from "@chakra-ui/toast"
import capitalize from "lodash/capitalize"
import React, { useEffect } from "react"
import { Trans, WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { RouteComponentProps, withRouter } from "react-router"
import { Dispatch } from "redux"
import AlertBox from "../../../application/components/common/AlertBox"
import { AppState } from "../../../application/store"
import { clearPasswordResetRequestStatusAction, requestPasswordResetAction } from "../store/Actions"
import RequestPasswordResetForm from "./RequestPasswordResetForm"
import UserFormBox from "./UserFormBox"
import { Link as RouterLink } from "react-router-dom"

const SUCCESS_TOAST_ID = "request-success"

type Props = Pick<WithTranslation, "t"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps> &
    Pick<RouteComponentProps, "history">

export const RequestPasswordResetPage: React.FC<Props> = ({
    t,
    actionResult,
    sendResetPasswordRequest,
    clearStatus,
    history,
}) => {
    useEffect(() => {
        return () => {
            clearStatus()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const toast = useToast({ position: "top", duration: 7000, isClosable: true })

    useEffect(() => {
        if (actionResult.status === "FINISHED") {
            if (!toast.isActive(SUCCESS_TOAST_ID)) {
                toast({ title: t("password-reset-request-page:request-success-message"), id: SUCCESS_TOAST_ID })
            }
            history.push("/")
        }
    }, [actionResult, t, toast, history])

    const loginLink = (
        <Trans ns="password-reset-request-page" i18nKey="back-to-login">
            <Text align="center">
                Don&apos;t have an account yet?
                <Link as={RouterLink} to="/">
                    Sign up
                </Link>
            </Text>
        </Trans>
    )

    return (
        <UserFormBox outsideElement={loginLink}>
            <Box display="block">
                {actionResult.status === "FAILED" && (
                    <AlertBox
                        title={capitalize(t("common:unexpected-error"))}
                        icon={true}
                        alertProps={{ status: "error", marginTop: "0.3rem" }}
                        descriptionProps={{ textAlign: "center" }}
                        onClose={clearStatus}
                    />
                )}
                <Box marginTop="1rem">
                    <Text fontSize="sm" color="gray.400" align="center">
                        {t("password-reset-request-page:provide-email-address")}
                    </Text>
                </Box>
                <RequestPasswordResetForm
                    onSubmit={({ email }) => sendResetPasswordRequest(email)}
                    submitInProgress={actionResult.status === "PENDING"}
                />
            </Box>
        </UserFormBox>
    )
}

const mapStateToProps = (state: AppState) => ({
    actionResult: state.users.passwordResetRequest,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    sendResetPasswordRequest: (email: string) => dispatch(requestPasswordResetAction.started(email)),
    clearStatus: () => dispatch(clearPasswordResetRequestStatusAction()),
})

export default withRouter(connect(mapStateToProps, mapDispatchToProps)(withTranslation()(RequestPasswordResetPage)))
