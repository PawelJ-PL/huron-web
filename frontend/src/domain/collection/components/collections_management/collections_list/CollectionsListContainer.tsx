import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import Loader from "../../../../../application/components/common/Loader"
import UnexpectedErrorMessage from "../../../../../application/components/common/UnexpectedErrorMessage"
import { AppState } from "../../../../../application/store"
import { fetchMultipleUsersPublicDataAction } from "../../../../user/store/Actions"
import { listCollectionsAction, resetAvailableCollectionsListAction } from "../../../store/Actions"
import CollectionsListPage from "./CollectionsListPage"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & Pick<WithTranslation, "t">

const CollectionsListContainer: React.FC<Props> = ({
    t,
    collections,
    fetchAllCollections,
    resetCollectionsList,
    knownUsers,
    fetchAuthors,
}) => {
    useEffect(() => {
        if (
            (collections.status === "PENDING" && collections.params === false) ||
            collections.status === "NOT_STARTED"
        ) {
            return
        }
        fetchAllCollections()
        return () => {
            resetCollectionsList()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (collections.status === "NOT_STARTED") {
            fetchAllCollections()
        }
    }, [collections, fetchAllCollections])

    useEffect(() => {
        if (collections.status === "FINISHED") {
            const ownerIds = collections.data.map((c) => c.owner)
            const uniqueOwnerIds = Array.from(new Set(ownerIds))
            const unknownUsers = uniqueOwnerIds.filter((id) => !Object.keys(knownUsers).includes(id))
            if (unknownUsers.length > 0) {
                fetchAuthors(unknownUsers)
            }
        }
    })

    if (collections.status === "FAILED") {
        return <UnexpectedErrorMessage error={collections.error} />
    } else if (collections.status === "FINISHED") {
        return <CollectionsListPage collections={collections.data} />
    } else {
        return <Loader title={t("collections-list-page:loader-text")} />
    }
}

const mapStateToProps = (state: AppState) => ({
    collections: state.collections.availableCollections,
    knownUsers: state.users.knownUsers,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchAllCollections: () => dispatch(listCollectionsAction.started(false)),
    resetCollectionsList: () => dispatch(resetAvailableCollectionsListAction()),
    fetchAuthors: (userIds: string[]) => dispatch(fetchMultipleUsersPublicDataAction.started(userIds)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(CollectionsListContainer))
