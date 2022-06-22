import { Table, TableContainer, Tbody, Th, Thead, Tr } from "@chakra-ui/react"
import React from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { Collection } from "../../../types/Collection"
import { CollectionPermission } from "../../../types/CollectionPermission"
import EditMemberPermissionsContainer from "./EditMemberPermissionsContainer"
import MemberPreviewEntry from "./MemberPreviewEntry"

type Props = {
    members: Record<string, CollectionPermission[]>
    collection: Collection
} & Pick<WithTranslation, "t">

const MembersPreview: React.FC<Props> = ({ members, collection, t }) => {
    return (
        <TableContainer>
            <EditMemberPermissionsContainer />
            <Table size={["sm", null, "md"]}>
                <Thead>
                    <Tr>
                        <Th></Th>
                        <Th textAlign="center" fontSize={["xx-small", null, "xs"]}>
                            {t("collection-permission:manage-collection")}
                        </Th>
                        <Th textAlign="center" fontSize={["xx-small", null, "xs"]}>
                            {t("collection-permission:modify-file")}
                        </Th>
                        <Th textAlign="center" fontSize={["xx-small", null, "xs"]}>
                            {t("collection-permission:create-file")}
                        </Th>
                        <Th textAlign="center" fontSize={["xx-small", null, "xs"]}>
                            {t("collection-permission:read-file")}
                        </Th>
                        <Th textAlign="center" fontSize={["xx-small", null, "xs"]}>
                            {t("collection-permission:read-file-metadata")}
                        </Th>
                        <Th></Th>
                    </Tr>
                </Thead>
                <Tbody>
                    {Object.entries(members).map(([memberId, permissions]) => (
                        <MemberPreviewEntry
                            key={memberId}
                            memberId={memberId}
                            permissions={permissions}
                            collection={collection}
                        />
                    ))}
                </Tbody>
            </Table>
        </TableContainer>
    )
}

export default withTranslation()(MembersPreview)
