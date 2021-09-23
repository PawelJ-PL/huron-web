import { Box } from "@chakra-ui/layout"
import React from "react"
import { Collection } from "../../../collection/types/Collection"
import { isDirectoryTree, isFileTree, ObjectTree } from "../../types/ObjectTree"
import DirectoryView from "./directory/DirectoryView"
import FileView from "./file/FileView"
import FileBreadCrumb from "./FileBreadcrumb"

type Props = {
    collection: Collection
    objectTree: ObjectTree
}

export const SingleObjectView: React.FC<Props> = ({ collection, objectTree }) => {
    return (
        <Box>
            <FileBreadCrumb collection={collection} objectTree={objectTree} />
            {isFileTree(objectTree) ? (
                <FileView metadata={objectTree.metadata} />
            ) : (
                <DirectoryView
                    childObjects={objectTree.children}
                    collectionId={collection.id}
                    thisDirectoryId={isDirectoryTree(objectTree) ? objectTree.metadata.id : null}
                />
            )}
        </Box>
    )
}

export default SingleObjectView
