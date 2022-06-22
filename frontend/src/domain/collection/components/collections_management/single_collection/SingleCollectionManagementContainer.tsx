import React, { useEffect } from "react"
import { connect } from "react-redux"
import { useNavigate, useParams } from "react-router-dom"
import { Dispatch } from "redux"
import EmptyPlaceholder from "../../../../../application/components/common/EmptyPlaceholder"
import { AppState } from "../../../../../application/store"
import {
    clearSetMemberPermissionsResultAction,
    getCollectionDetailsAction,
    getCollectionMembersAction,
    listMyPermissionsToCollectionActions,
    resetAddMemberResultAction,
    resetCollectionMembersResultAction,
    resetDeleteCollectionResultAction,
    resetDeleteMemberStatsAction,
} from "../../../store/Actions"
import { RiForbid2Line } from "react-icons/ri"
import { withTranslation, WithTranslation } from "react-i18next"
import Loader from "../../../../../application/components/common/Loader"
import UnexpectedErrorMessage from "../../../../../application/components/common/UnexpectedErrorMessage"
import SingleCollectionManagementPage from "./SingleCollectionManagementPage"
import { fetchMultipleUsersPublicDataAction } from "../../../../user/store/Actions"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & Pick<WithTranslation, "t">

export const SingleCollectionManagementContainer: React.FC<Props> = ({
    collection,
    members,
    myPermissions,
    myId,
    knownUsers,
    getCollectionDetails,
    getCollectionMembers,
    resetCollectionMembersResult,
    fetchMyPermissions,
    fetchMembersDetails,
    fetchError,
    deleteCollectionResult,
    resetDeleteCollectionResult,
    deleteMemberResult,
    resetDeleteMemberResult,
    resetAddMemberResult,
    clearSetPermissionsResult,
    t,
}) => {
    const { collectionId = "" } = useParams<{ collectionId: string }>()

    const navigate = useNavigate()

    const maybeMyPermissions =
        (myPermissions.status === "FINISHED" && myPermissions.params === collectionId && myPermissions.data) ||
        (members.status === "FINISHED" && members.params === collectionId && members.data?.[myId]) ||
        null

    useEffect(() => {
        if (collection.status !== "PENDING" || collection.params !== collectionId) {
            getCollectionDetails(collectionId)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (members.status !== "PENDING" || members.params !== collectionId) {
            getCollectionMembers(collectionId)
        }
        return () => {
            resetCollectionMembersResult()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (collection.status === "FINISHED" && members.status === "FINISHED" && !members.data?.[myId]) {
            fetchMyPermissions(collectionId)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [collection, members, fetchMyPermissions])

    useEffect(() => {
        if (members.status === "FINISHED") {
            const memberIds = Object.keys(members.data ?? {})
            const uniqueMemberIds = Array.from(new Set(memberIds))
            const unknownUsers = uniqueMemberIds.filter((id) => !Object.keys(knownUsers).includes(id))
            if (unknownUsers.length > 0) {
                fetchMembersDetails(unknownUsers)
            }
        }
    }, [fetchMembersDetails, knownUsers, members])

    useEffect(() => {
        if (
            deleteCollectionResult.status === "FINISHED" &&
            collection.status !== "NOT_STARTED" &&
            collection.params === deleteCollectionResult.params
        ) {
            navigate("/")
        }
    }, [collection, deleteCollectionResult, navigate])

    const resetActionsResult = () => {
        resetDeleteCollectionResult()
        resetDeleteMemberResult()
        resetAddMemberResult()
        clearSetPermissionsResult()
    }

    useEffect(() => {
        resetActionsResult()
        return () => {
            resetActionsResult()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (deleteMemberResult.status !== "FINISHED" || collection.status !== "FINISHED") {
            return
        }
        if (
            deleteMemberResult.params.collectionId === collection.params &&
            deleteMemberResult.params.memberId === myId
        ) {
            navigate("/")
        }
    }, [collection, deleteMemberResult, myId, navigate])

    if (fetchError) {
        return <UnexpectedErrorMessage error={fetchError} />
    }
    if (collection.status === "FINISHED" && collection.data && members.status === "FINISHED" && maybeMyPermissions) {
        return (
            <SingleCollectionManagementPage
                collection={collection.data}
                myPermissions={maybeMyPermissions}
                members={members.data ?? undefined}
                isOwner={collection.data.owner === myId}
            />
        )
    } else if (collection.status === "FINISHED" && !collection.data) {
        return (
            <EmptyPlaceholder
                text={t("collection-manage-page:collection-not-found-placeholder")}
                icon={RiForbid2Line}
            />
        )
    } else {
        return <Loader title={t("collection-manage-page:loading-collection-data")} />
    }
}

const mapStateToProps = (state: AppState) => ({
    collection: state.collections.collectionDetails,
    members: state.collections.collectionMembers,
    myPermissions: state.collections.myPermissions,
    myId: state.users.userData.status === "FINISHED" ? state.users.userData.data.id : "",
    fetchError:
        (state.collections.collectionDetails.status === "FAILED" ? state.collections.collectionDetails.error : null) ??
        (state.collections.collectionMembers.status === "FAILED" ? state.collections.collectionMembers.error : null) ??
        (state.collections.myPermissions.status === "FAILED" ? state.collections.myPermissions.error : null),
    knownUsers: state.users.knownUsers,
    deleteCollectionResult: state.collections.deleteCollectionResult,
    deleteMemberResult: state.collections.deleteMember,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    getCollectionDetails: (collectionId: string) => dispatch(getCollectionDetailsAction.started(collectionId)),
    getCollectionMembers: (collectionId: string) => dispatch(getCollectionMembersAction.started(collectionId)),
    resetCollectionMembersResult: () => dispatch(resetCollectionMembersResultAction()),
    fetchMyPermissions: (collectionId: string) => dispatch(listMyPermissionsToCollectionActions.started(collectionId)),
    fetchMembersDetails: (userIds: string[]) => dispatch(fetchMultipleUsersPublicDataAction.started(userIds)),
    resetDeleteCollectionResult: () => dispatch(resetDeleteCollectionResultAction()),
    resetDeleteMemberResult: () => dispatch(resetDeleteMemberStatsAction()),
    resetAddMemberResult: () => dispatch(resetAddMemberResultAction()),
    clearSetPermissionsResult: () => dispatch(clearSetMemberPermissionsResultAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(SingleCollectionManagementContainer))
