import React from "react"
import { Collection } from "../../../types/Collection"
import { CollectionPermission } from "../../../types/CollectionPermission"

type Props = {
    collection: Collection
    myPermissions: CollectionPermission[]
    members?: Record<string, CollectionPermission[]>
}

const SingleCollectionManagementPage: React.FC<Props> = ({ collection, myPermissions, members }) => {
    return (
        <div>
            <div>{JSON.stringify(collection)}</div>
            <div>{JSON.stringify(myPermissions)}</div>
            <div>{JSON.stringify(members)}</div>
        </div>
    )
}

export default SingleCollectionManagementPage
