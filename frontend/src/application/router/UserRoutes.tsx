/* eslint-disable react/display-name */

import React from "react"
import CollectionsContainer from "../../domain/collection/components/CollectionsContainer"
import SingleCollectionContainer from "../../domain/collection/components/SingleCollectionContainer"
import UserProfilePage from "../../domain/user/components/profile/UserProfilePage"
import { AppRoute } from "./AppRoute"

export const userRoutes: AppRoute[] = [
    {
        path: "/",
        exact: true,
        withLayout: true,
        component: CollectionsContainer,
    },
    {
        path: "/profile",
        exact: true,
        withLayout: true,
        component: UserProfilePage,
    },
    {
        path: "/collection/:collectionId",
        exact: true,
        withLayout: true,
        render: (props) => <SingleCollectionContainer key={props.match.params.collectionId} />,
    },
]
