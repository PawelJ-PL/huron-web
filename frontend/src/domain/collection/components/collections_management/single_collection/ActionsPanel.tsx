import { Button, ButtonGroup, useDisclosure, useToast } from "@chakra-ui/react"
import React, { useState } from "react"
import { withTranslation, WithTranslation } from "react-i18next"
import { FaTrashAlt } from "react-icons/fa"
import { ImExit } from "react-icons/im"
import { FaUserPlus } from "react-icons/fa"
import { CgInternal } from "react-icons/cg"
import { CollectionPermission } from "../../../types/CollectionPermission"
import Confirmation from "../../../../../application/components/common/Confirmation"
import { Collection } from "../../../types/Collection"
import { AppState } from "../../../../../application/store"
import { Dispatch } from "redux"
import {
    deleteCollectionAction,
    deleteMemberAction,
    setActiveCollectionAction,
    setPreferredCollectionIdAction,
} from "../../../store/Actions"
import { connect } from "react-redux"
import FindUserModal from "../../../../user/components/contacts/FindUserModal"
import { UserPublicData } from "../../../../user/types/UserPublicData"
import AddOrUpdateMemberModal from "./AddOrUpdateMemberModal"
import { useNavigate } from "react-router-dom"

type Props = {
    isOwner: boolean
    myPermissions: CollectionPermission[]
    collection: Collection
    memberIds: string[]
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

const ActionsPanel: React.FC<Props> = ({
    t,
    isOwner,
    myPermissions,
    collection,
    deleteCollection,
    memberIds,
    masterKey,
    userId,
    leaveCollection,
    setPreferredCollection,
    setActiveCollection,
}) => {
    const deleteConfirmation = useDisclosure()
    const findNewMemberModal = useDisclosure()
    const leaveCollectionConfirmation = useDisclosure()

    const [memberToAdd, setMemberToAdd] = useState<UserPublicData | null>(null)

    const toast = useToast({ position: "top", isClosable: true })
    const navigate = useNavigate()

    const onUserSelected = (user: UserPublicData) =>
        memberIds.includes(user.userId)
            ? toast({ status: "warning", title: t("collection-manage-page:user-already-member") })
            : setMemberToAdd(user)

    return (
        <ButtonGroup size="sm" colorScheme="brand">
            <Confirmation
                isOpen={deleteConfirmation.isOpen}
                onClose={deleteConfirmation.onClose}
                onConfirm={() => deleteCollection(collection.id)}
                content={t("collection-manage-page:collection-delete-confirmation", {
                    collectionName: collection.name,
                })}
            />
            <FindUserModal
                isOpen={findNewMemberModal.isOpen}
                onClose={findNewMemberModal.onClose}
                onSelect={onUserSelected}
                excludeContacts={false}
            />
            {userId && (
                <Confirmation
                    isOpen={leaveCollectionConfirmation.isOpen}
                    onClose={leaveCollectionConfirmation.onClose}
                    onConfirm={() => leaveCollection(collection.id, userId)}
                    title={t("collection-manage-page:leave-collection-confirmation.title", {
                        collectionName: collection.name,
                    })}
                    content={t("collection-manage-page:leave-collection-confirmation.content")}
                />
            )}
            {memberToAdd && masterKey.status === "FINISHED" && (
                <AddOrUpdateMemberModal
                    isOpen={true}
                    onClose={() => setMemberToAdd(null)}
                    collection={collection}
                    user={memberToAdd}
                    operationProps={{ masterKey: masterKey.data }}
                />
            )}
            <Button
                leftIcon={<CgInternal />}
                onClick={() => {
                    setActiveCollection(collection.id)
                    setPreferredCollection(collection.id)
                    navigate(`/collection/${collection.id}`)
                }}
            >
                {t("collection-manage-page:go-to-collection")}
            </Button>
            {isOwner && (
                <Button colorScheme="red" leftIcon={<FaTrashAlt />} onClick={deleteConfirmation.onOpen}>
                    {t("collection-manage-page:collection-action-buttons.remove-collection")}
                </Button>
            )}
            {!isOwner && (
                <Button colorScheme="red" leftIcon={<ImExit />} onClick={leaveCollectionConfirmation.onOpen}>
                    {t("collection-manage-page:collection-action-buttons.leave-collection")}
                </Button>
            )}
            {myPermissions.includes("ManageCollection") && (
                <Button
                    leftIcon={<FaUserPlus />}
                    onClick={() =>
                        masterKey.status === "FINISHED"
                            ? findNewMemberModal.onOpen()
                            : toast({
                                  status: "warning",
                                  title: t("collection-manage-page:unable-to-add-member-key-locked"),
                              })
                    }
                >
                    {t("collection-manage-page:collection-action-buttons.invite-member")}
                </Button>
            )}
        </ButtonGroup>
    )
}

const mapStateToProps = (state: AppState) => ({
    masterKey: state.users.masterKey,
    userId: state.users.userData.status === "FINISHED" ? state.users.userData.data.id : undefined,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    deleteCollection: (collectionId: string) => dispatch(deleteCollectionAction.started(collectionId)),
    leaveCollection: (collectionId: string, memberId: string) =>
        dispatch(deleteMemberAction.started({ memberId, collectionId })),
    setPreferredCollection: (collectionId: string) => dispatch(setPreferredCollectionIdAction.started(collectionId)),
    setActiveCollection: (collectionId: string) => dispatch(setActiveCollectionAction(collectionId)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(ActionsPanel))
