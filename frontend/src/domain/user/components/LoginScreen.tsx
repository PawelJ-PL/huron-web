import { Box, Text } from "@chakra-ui/layout"
import React from "react"
import { AppState } from "../../../application/store"
import { Dispatch } from "redux"
import { loginAction, resetLoginResultAction } from "../store/Actions"
import { connect } from "react-redux"
import LoginForm from "./LoginForm"
import { Trans, WithTranslation, withTranslation } from "react-i18next"
import { Link } from "@chakra-ui/layout"
import { Link as RouterLink } from "react-router-dom"
import UserFormBox from "./UserFormBox"
import UnexpectedErrorMessage from "../../../application/components/common/UnexpectedErrorMessage"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & WithTranslation

const LoginScreen: React.FC<Props> = ({ login, loginResult, resetLoginResult, t }) => {
    const signUpLink = (
        <Trans ns="login-page" i18nKey="sign-up-invitation">
            <Text align="center">
                Don&apos;t have an account yet?
                <Link as={RouterLink} to="/signup">
                    Sign up
                </Link>
            </Text>
        </Trans>
    )

    return (
        <UserFormBox outsideElement={signUpLink}>
            {loginResult.status === "FAILED" && (
                <UnexpectedErrorMessage
                    error={loginResult.error}
                    alertProps={{ marginTop: "0.3rem" }}
                    onClose={resetLoginResult}
                />
            )}
            <LoginForm
                onSubmit={(formData) => login(formData.email, formData.password)}
                submitInProgress={loginResult.status === "PENDING"}
            />
            <Link as={RouterLink} to="/reset-password" marginTop="0.2rem">
                {t("login-page:forgot-my-password")}
            </Link>
            <Box minHeight="1.5em" marginTop="1rem">
                {loginResult.status === "FINISHED" && loginResult.data === false && (
                    <Text fontSize="xs" color="red.500">
                        {t("login-page:invalid-username-or-password")}
                    </Text>
                )}
            </Box>
        </UserFormBox>
    )
}

const mapStateToProps = (state: AppState) => ({
    loginResult: state.users.loginStatus,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    login: (email: string, password: string) => dispatch(loginAction.started({ email, password })),
    resetLoginResult: () => dispatch(resetLoginResultAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(LoginScreen))
