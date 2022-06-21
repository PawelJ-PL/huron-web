import { Breadcrumb, BreadcrumbItem, BreadcrumbLink } from "@chakra-ui/breadcrumb"
import { Text } from "@chakra-ui/layout"
import { Tooltip } from "@chakra-ui/react"
import React from "react"
import { Link } from "react-router-dom"
import { Collection } from "../../../collection/types/Collection"
import { isRootDirectoryTree, ObjectTree } from "../../types/ObjectTree"

type Props = {
    collection: Collection
    objectTree: ObjectTree
}

type Item = { name: string; link: string }

const FileBreadCrumb: React.FC<Props> = ({ collection, objectTree }) => {
    const root: Item = { name: collection.name, link: `/collection/${collection.id}` }

    const parents: Item[] = isRootDirectoryTree(objectTree)
        ? []
        : objectTree.parents.map((parent) => ({
              name: parent.name,
              link: `/collection/${collection.id}/file/${parent.id}`,
          }))

    const selfItemAsArray: Item[] = isRootDirectoryTree(objectTree)
        ? []
        : [{ name: objectTree.metadata.name, link: `/collection/${collection.id}/file/${objectTree.metadata.id}` }]

    const merged = [root].concat(parents.reverse()).concat(selfItemAsArray)

    return (
        <Breadcrumb>
            {merged.map((item) => (
                <BreadcrumbItem key={item.link}>
                    <Tooltip label={item.name} aria-label={item.name} hasArrow={true}>
                        <BreadcrumbLink as={Link} to={item.link}>
                            <Text noOfLines={1} wordBreak="break-all" maxWidth="30vw">
                                {item.name}
                            </Text>
                        </BreadcrumbLink>
                    </Tooltip>
                </BreadcrumbItem>
            ))}
        </Breadcrumb>
    )
}

export default FileBreadCrumb
