import { Alert } from "@chakra-ui/alert"
import { Box, Text } from "@chakra-ui/layout"
import { useToast } from "@chakra-ui/react"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { useNavigate, useParams } from "react-router-dom"
import { Dispatch } from "redux"
import UnexpectedErrorMessage from "../../../application/components/common/UnexpectedErrorMessage"
import { AppState } from "../../../application/store"
import { clearResetPasswordStatusAction, resetPasswordAction } from "../store/Actions"
import ResetPasswordForm from "./ResetPasswordForm"
import UserFormBox from "./UserFormBox"

const SUCCESS_TOAST_ID = "password-reset-success"

type Props = Pick<WithTranslation, "t"> & ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps>

export const ResetPasswordPage: React.FC<Props> = ({ t, actionResult, resetPassword, clearActionResult }) => {
    const navigate = useNavigate()
    const { token = "" } = useParams<{ token: string }>()

    useEffect(() => {
        return () => {
            clearActionResult()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const toast = useToast({ position: "top", duration: 3000, isClosable: true })

    useEffect(() => {
        if (actionResult.status === "FINISHED" && actionResult.data) {
            if (!toast.isActive(SUCCESS_TOAST_ID)) {
                toast({
                    title: t("reset-password-page:password-has-been-reset"),
                    id: SUCCESS_TOAST_ID,
                    status: "success",
                })
            }
            navigate("/", { replace: true })
        }
    }, [actionResult, t, navigate, toast])

    return (
        <UserFormBox>
            {actionResult.status === "FAILED" && (
                <UnexpectedErrorMessage
                    error={actionResult.error}
                    alertProps={{ marginTop: "0.3rem" }}
                    onClose={clearActionResult}
                />
            )}
            <Box>
                <Alert status="warning" marginY="1rem">
                    {t("reset-password-page:access-to-documents-will-be-lost")}
                </Alert>
                <ResetPasswordForm
                    onSubmit={({ password, email }) => resetPassword(token, password, email)}
                    submitInProgress={actionResult.status === "PENDING"}
                />
                {actionResult.status === "FINISHED" && actionResult.data === false && (
                    <Text fontSize="xs" color="red.500" marginTop="0.5rem">
                        {t("reset-password-page:invalid-token-or-email")}
                    </Text>
                )}
            </Box>
        </UserFormBox>
    )
}

const mapStateToProps = (state: AppState) => ({
    actionResult: state.users.resetPassword,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    resetPassword: (resetToken: string, newPassword: string, email: string) =>
        dispatch(resetPasswordAction.started({ resetToken, newPassword, email })),
    clearActionResult: () => dispatch(clearResetPasswordStatusAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(ResetPasswordPage))
