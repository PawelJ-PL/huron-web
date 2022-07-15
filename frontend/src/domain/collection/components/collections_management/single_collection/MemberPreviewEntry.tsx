import { Flex, Icon, IconButton, Td, Tooltip, Tr } from "@chakra-ui/react"
import React from "react"
import Author from "../../../../file/components/metadata_view/file/Author"
import { CollectionPermission } from "../../../types/CollectionPermission"
import { IoMdCheckmarkCircle } from "react-icons/io"
import { MdOutlineBlock } from "react-icons/md"
import { FaEdit } from "react-icons/fa"
import { FaTrashAlt } from "react-icons/fa"
import { WithTranslation, withTranslation } from "react-i18next"
import { Dispatch } from "redux"
import { Collection } from "../../../types/Collection"
import { requestMemberDeleteAction, requestPermissionsChangeForMemberAction } from "../../../store/Actions"
import { connect } from "react-redux"
import { AppState } from "../../../../../application/store"

type Props = {
    collection: Collection
    memberId: string
    permissions: CollectionPermission[]
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

const MemberPreviewEntry: React.FC<Props> = ({
    collection,
    memberId,
    permissions,
    requestPermissionsUpdate,
    myId,
    requestDeleteMember,
    t,
}) => {
    const iconForPermission = (permission: CollectionPermission) =>
        permissions.includes(permission) ? (
            <Icon as={IoMdCheckmarkCircle} color="green.500" />
        ) : (
            <Icon as={MdOutlineBlock} color="red.500" />
        )

    return (
        <>
            <Tr>
                <Td>
                    <Author authorId={memberId} />
                </Td>
                <Td textAlign="center">{iconForPermission("ManageCollection")}</Td>
                <Td textAlign="center">{iconForPermission("ModifyFile")}</Td>
                <Td textAlign="center">{iconForPermission("CreateFile")}</Td>
                <Td textAlign="center">{iconForPermission("ReadFile")}</Td>
                <Td textAlign="center">{iconForPermission("ReadFileMetadata")}</Td>
                <Td>
                    <Flex>
                        {collection.owner !== memberId && memberId !== myId && (
                            <Tooltip label={t("collection-manage-page:member-actions.edit-permissions")}>
                                <IconButton
                                    icon={<FaEdit />}
                                    aria-label={t("collection-manage-page:member-actions.edit-permissions")}
                                    variant="link"
                                    colorScheme="brand"
                                    onClick={() => requestPermissionsUpdate(collection, memberId, permissions)}
                                />
                            </Tooltip>
                        )}
                        {collection.owner !== memberId && memberId !== myId && (
                            <Tooltip label={t("collection-manage-page:member-actions.remove-member")}>
                                <IconButton
                                    icon={<FaTrashAlt />}
                                    aria-label={t("collection-manage-page:member-actions.remove-member")}
                                    variant="link"
                                    colorScheme="red"
                                    onClick={() => requestDeleteMember(memberId)}
                                />
                            </Tooltip>
                        )}
                    </Flex>
                </Td>
            </Tr>
        </>
    )
}

const mapStateToProps = (state: AppState) => ({
    myId: state.users.userData.status === "FINISHED" ? state.users.userData.data.id : undefined,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    requestPermissionsUpdate: (collection: Collection, memberId: string, currentPermissions: CollectionPermission[]) =>
        dispatch(requestPermissionsChangeForMemberAction({ collection, memberId, currentPermissions })),
    requestDeleteMember: (memberId: string) => dispatch(requestMemberDeleteAction(memberId)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(MemberPreviewEntry))
