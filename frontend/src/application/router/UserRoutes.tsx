/* eslint-disable react/display-name */

import React from "react"
import CollectionsContainer from "../../domain/collection/components/CollectionsContainer"
import SingleCollectionContainer from "../../domain/collection/components/SingleCollectionContainer"
import ContactsContainer from "../../domain/user/components/contacts/ContactsContainer"
import UserProfilePage from "../../domain/user/components/profile/UserProfilePage"
import UserPublicDataContainer from "../../domain/user/components/public_data/UserPublicDataContainer"
import { AppRoute } from "./AppRoute"

export const userRoutes: AppRoute[] = [
    {
        path: "/",
        withLayout: true,
        element: <CollectionsContainer />,
    },
    {
        path: "/profile",
        withLayout: true,
        element: <UserProfilePage />,
    },
    {
        path: "/collection/:collectionId",
        withLayout: true,
        element: <SingleCollectionContainer />,
    },
    {
        path: "/collection/:collectionId/file/:fileId",
        withLayout: true,
        element: <SingleCollectionContainer />,
    },
    {
        path: "/user/:userId",
        withLayout: true,
        element: <UserPublicDataContainer />,
    },
    {
        path: "/contacts",
        withLayout: true,
        element: <ContactsContainer />,
    },
]
