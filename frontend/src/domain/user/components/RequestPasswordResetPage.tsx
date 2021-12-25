import { Box, Link, Text } from "@chakra-ui/layout"
import { useToast } from "@chakra-ui/react"
import React, { useEffect } from "react"
import { Trans, WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { AppState } from "../../../application/store"
import { clearPasswordResetRequestStatusAction, requestPasswordResetAction } from "../store/Actions"
import RequestPasswordResetForm from "./RequestPasswordResetForm"
import UserFormBox from "./UserFormBox"
import { Link as RouterLink, useNavigate } from "react-router-dom"
import UnexpectedErrorMessage from "../../../application/components/common/UnexpectedErrorMessage"

const SUCCESS_TOAST_ID = "request-success"

type Props = Pick<WithTranslation, "t"> & ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps>

export const RequestPasswordResetPage: React.FC<Props> = ({
    t,
    actionResult,
    sendResetPasswordRequest,
    clearStatus,
}) => {
    const navigate = useNavigate()

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
            navigate("/")
        }
    }, [actionResult, t, toast, navigate])

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
                    <UnexpectedErrorMessage
                        error={actionResult.error}
                        alertProps={{ marginTop: "0.3rem" }}
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

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(RequestPasswordResetPage))
