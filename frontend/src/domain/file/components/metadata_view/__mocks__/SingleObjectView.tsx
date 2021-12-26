import React from "react"
import { Link } from "react-router-dom"
import { Collection } from "../../../../collection/types/Collection"
import { ObjectTree } from "../../../types/ObjectTree"

type Props = {
    collection: Collection
    objectTree: ObjectTree
}

const SingleObjectView: React.FC<Props> = ({ collection, objectTree }) => {
    const children = "children" in objectTree ? objectTree.children : []

    return (
        <div data-testid="OBJECT_VIEW_MOCK">
            <div>Collection: {collection.id}</div>
            <div>Tree: {JSON.stringify(objectTree)}</div>
            <div>
                Children:
                {children.map((c) => (
                    <Link key={c.id} to={`/collection/${collection.id}/file/${c.id}`}>{`Go to ${c.name}`}</Link>
                ))}
            </div>
        </div>
    )
}

export default SingleObjectView
