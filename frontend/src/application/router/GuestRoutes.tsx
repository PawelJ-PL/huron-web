import AccountActivationContainer from "../../domain/user/components/AccountActivationContainer"
import RequestPasswordResetPage from "../../domain/user/components/RequestPasswordResetPage"
import ResetPasswordPage from "../../domain/user/components/ResetPasswordPage"
import SignupScreen from "../../domain/user/components/SignupScreen"
import { AppRoute } from "./AppRoute"

export const guestRoutes: AppRoute[] = [
    {
        path: "/signup",
        exact: true,
        component: SignupScreen,
    },
    {
        path: "/account-activation/:token",
        exact: true,
        component: AccountActivationContainer,
    },
    {
        path: "/reset-password",
        exact: true,
        component: RequestPasswordResetPage,
    },
    {
        path: "/set-password/:token",
        exact: true,
        component: ResetPasswordPage,
    },
]
