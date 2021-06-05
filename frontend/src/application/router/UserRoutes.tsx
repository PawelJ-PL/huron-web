/* eslint-disable react/display-name */
import React from "react"
import UserProfilePage from "../../domain/user/components/profile/UserProfilePage"
import { AppRoute } from "./AppRoute"

export const userRoutes: AppRoute[] = [
    {
        path: "/",
        exact: true,
        withLayout: true,
        render: () => <div>HOME</div>,
    },
    {
        path: "/profile",
        exact: true,
        withLayout: true,
        component: UserProfilePage,
    },
]
