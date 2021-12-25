import React from "react"
import AccountActivationContainer from "../../domain/user/components/AccountActivationContainer"
import RequestPasswordResetPage from "../../domain/user/components/RequestPasswordResetPage"
import ResetPasswordPage from "../../domain/user/components/ResetPasswordPage"
import SignupScreen from "../../domain/user/components/SignupScreen"
import { AppRoute } from "./AppRoute"

export const guestRoutes: AppRoute[] = [
    {
        path: "/signup",
        element: <SignupScreen />,
    },
    {
        path: "/account-activation/:token",
        element: <AccountActivationContainer />,
    },
    {
        path: "/reset-password",
        element: <RequestPasswordResetPage />,
    },
    {
        path: "/set-password/:token",
        element: <ResetPasswordPage />,
    },
]
