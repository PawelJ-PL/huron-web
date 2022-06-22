import { Box, Divider, Heading } from "@chakra-ui/react"
import React from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import AlertBox from "../../../../../application/components/common/AlertBox"
import Confirmation from "../../../../../application/components/common/Confirmation"
import LoadingOverlay from "../../../../../application/components/common/LoadingOverlay"
import UnexpectedErrorMessage from "../../../../../application/components/common/UnexpectedErrorMessage"
import { AppState } from "../../../../../application/store"
import { CollectionNotEmpty } from "../../../api/errors"
import {
    clearSetMemberPermissionsResultAction,
    deleteMemberAction,
    requestMemberDeleteAction,
    resetAddMemberResultAction,
    resetDeleteCollectionResultAction,
    resetDeleteMemberStatsAction,
} from "../../../store/Actions"
import { Collection } from "../../../types/Collection"
import { CollectionPermission } from "../../../types/CollectionPermission"
import ActionsPanel from "./ActionsPanel"
import MembersPreview from "./MembersPreview"

type Props = {
    collection: Collection
    myPermissions: CollectionPermission[]
    members?: Record<string, CollectionPermission[]>
    isOwner: boolean
} & ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps> &
    Pick<WithTranslation, "t">

const SingleCollectionManagementPage: React.FC<Props> = ({
    collection,
    myPermissions,
    members,
    isOwner,
    deleteCollectionError,
    resetDeleteCollectionStatus,
    actionInProgress,
    addMemberError,
    resetAddMemberStatus,
    deleteMemberError,
    resetDeleteMemberStatus,
    setPermissionsError,
    resetSetPermissionsStatus,
    memberToDelete,
    unsetMemberToDelete,
    deleteMember,
    t,
}) => {
    const renderError = (error: Error, onClose: () => void) => {
        let handledErrorMessage = ""

        if (error instanceof CollectionNotEmpty) {
            handledErrorMessage = t("collection-manage-page:collection-not-empty-error")
        }

        if (handledErrorMessage) {
            return <AlertBox icon={true} onClose={onClose} status="warning" title={handledErrorMessage} />
        }

        return <UnexpectedErrorMessage error={error} onClose={onClose} />
    }

    return (
        <Box>
            {deleteCollectionError && renderError(deleteCollectionError, resetDeleteCollectionStatus)}
            {addMemberError && renderError(addMemberError, resetAddMemberStatus)}
            {deleteMemberError && renderError(deleteMemberError, resetDeleteMemberStatus)}
            {setPermissionsError && renderError(setPermissionsError, resetSetPermissionsStatus)}
            <LoadingOverlay active={actionInProgress} text={t("common:action-in-progress")} />
            {memberToDelete && (
                <Confirmation
                    isOpen={true}
                    onClose={unsetMemberToDelete}
                    onConfirm={() => deleteMember(memberToDelete, collection.id)}
                    content={t("collection-manage-page:remove-member-confirmation")}
                />
            )}
            <Heading as="h3" size="lg">
                {collection.name}
            </Heading>
            <Divider opacity="1" borderBottomWidth="2px" marginBottom="0.5rem" />
            <ActionsPanel
                isOwner={isOwner}
                myPermissions={myPermissions}
                collection={collection}
                memberIds={Object.keys(members ?? {})}
            />
            {members && (
                <Box marginTop="1rem">
                    <Heading as="h4" size="md">
                        {t("collection-manage-page:members")}
                    </Heading>
                    <MembersPreview members={members} collection={collection} />
                </Box>
            )}
        </Box>
    )
}

const mapStateToProps = (state: AppState) => ({
    actionInProgress: [state.collections.deleteCollectionResult.status, state.collections.addMember.status].includes(
        "PENDING"
    ),
    deleteCollectionError:
        state.collections.deleteCollectionResult.status === "FAILED"
            ? state.collections.deleteCollectionResult.error
            : undefined,

    addMemberError: state.collections.addMember.status === "FAILED" ? state.collections.addMember.error : undefined,
    deleteMemberError:
        state.collections.deleteMember.status === "FAILED" ? state.collections.deleteMember.error : undefined,
    setPermissionsError:
        state.collections.setPermissions.status === "FAILED" ? state.collections.setPermissions.error : undefined,
    memberToDelete: state.collections.requestDeleteMember,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    resetDeleteCollectionStatus: () => dispatch(resetDeleteCollectionResultAction()),
    resetAddMemberStatus: () => dispatch(resetAddMemberResultAction()),
    resetDeleteMemberStatus: () => dispatch(resetDeleteMemberStatsAction()),
    resetSetPermissionsStatus: () => dispatch(clearSetMemberPermissionsResultAction()),
    unsetMemberToDelete: () => dispatch(requestMemberDeleteAction(null)),
    deleteMember: (memberId: string, collectionId: string) =>
        dispatch(deleteMemberAction.started({ memberId, collectionId })),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(SingleCollectionManagementPage))
