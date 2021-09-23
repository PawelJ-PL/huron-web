import { Box, Link, Text } from "@chakra-ui/layout"
import { useToast } from "@chakra-ui/toast"
import capitalize from "lodash/capitalize"
import React, { useEffect } from "react"
import { Trans, WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { RouteComponentProps, withRouter } from "react-router"
import { Dispatch } from "redux"
import { AppState } from "../../../application/store"
import { UserAlreadyRegistered } from "../api/errors"
import { registerNewUserAction, resetRegistrationStatusAction } from "../store/Actions"
import SignupForm from "./SignupForm"
import UserFormBox from "./UserFormBox"
import { Link as RouterLink } from "react-router-dom"
import UnexpectedErrorMessage from "../../../application/components/common/UnexpectedErrorMessage"

type Props = ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps> &
    WithTranslation &
    Pick<RouteComponentProps, "history">

const SUCCESS_TOAST_ID = "signup-success"

export const SignupScreen: React.FC<Props> = ({ signupResult, i18n, signup, resetResult, t, history }) => {
    const selectedLanguage = capitalize(i18n.languages[0].split("-")[0])

    const toast = useToast({ position: "top", duration: 7000, isClosable: true })

    useEffect(() => {
        return () => {
            resetResult()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (signupResult.status === "FINISHED") {
            history.push("/")
            if (!toast.isActive(SUCCESS_TOAST_ID)) {
                toast({ title: t("signup-page:signup-success-message"), id: SUCCESS_TOAST_ID })
            }
        }
    }, [signupResult, toast, t, history])

    const generateKeysAndSignup = (formData: {
        nickname: string
        email: string
        password: string
        language: string
    }) => {
        signup(formData.nickname, formData.email, formData.password, selectedLanguage)
    }

    const loginLink = (
        <Trans ns="signup-page" i18nKey="back-to-login">
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
            {signupResult.status === "FAILED" && !(signupResult.error instanceof UserAlreadyRegistered) && (
                <UnexpectedErrorMessage
                    error={signupResult.error}
                    alertProps={{ marginTop: "0.3rem" }}
                    onClose={resetResult}
                />
            )}
            <SignupForm onSubmit={generateKeysAndSignup} submitInProgress={signupResult.status === "PENDING"} />
            <Box minHeight="2.5em" marginTop="1rem">
                {signupResult.status === "FAILED" && signupResult.error instanceof UserAlreadyRegistered && (
                    <Text fontSize="xs" color="red.500">
                        {t("signup-page:user-already-registered")}
                    </Text>
                )}
            </Box>
        </UserFormBox>
    )
}

const mapStateToProps = (state: AppState) => ({
    signupResult: state.users.userRegistration,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    signup: (nickname: string, email: string, password: string, language?: string) =>
        dispatch(registerNewUserAction.started({ nickname, email, password, language })),
    resetResult: () => dispatch(resetRegistrationStatusAction()),
})

export default withRouter(connect(mapStateToProps, mapDispatchToProps)(withTranslation()(SignupScreen)))
