import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Navigate, useNavigate } from "react-router-dom"
import { Dispatch } from "redux"
import AlertBox from "../../../application/components/common/AlertBox"
import Loader from "../../../application/components/common/Loader"
import { AppState } from "../../../application/store"
import {
    getPreferredCollectionIdAction,
    listCollectionsAction,
    removePreferredCollectionIdAction,
    resetAvailableCollectionsListAction,
} from "../store/Actions"
import AutoCreateCollectionModal from "./AutoCreateCollectionModal"
import SelectCollectionModal from "./SelectCollectionModal"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & Pick<WithTranslation, "t">

export const CollectionsContainer: React.FC<Props> = ({
    collections,
    fetchCollections,
    t,
    preferredCollection,
    getPreferredCollection,
    removePreferredCollection,
    removePreferredCollectionResult,
    cleanCollectionsData,
}) => {
    const navigate = useNavigate()

    useEffect(() => {
        if (preferredCollection.status !== "FINISHED" && preferredCollection.status !== "PENDING") {
            getPreferredCollection()
        }
        return () => {
            cleanCollectionsData()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (preferredCollection.status === "FINISHED" && preferredCollection.data !== null) {
            if (/^[0-9A-Fa-f-]+$/.test(preferredCollection.data)) {
                navigate(`/collection/${preferredCollection.data}`)
            } else {
                removePreferredCollection()
            }
        } else if (["FINISHED", "FAILED"].includes(preferredCollection.status)) {
            if (collections.status === "NOT_STARTED") {
                fetchCollections()
            }
        }
    }, [preferredCollection, collections, fetchCollections, navigate, removePreferredCollection])

    useEffect(() => {
        if (removePreferredCollectionResult.status === "FINISHED") {
            getPreferredCollection()
        }
    }, [getPreferredCollection, removePreferredCollectionResult])

    if (collections.status === "FINISHED" && collections.data.length < 1) {
        return <AutoCreateCollectionModal />
    } else if (collections.status === "FINISHED" && collections.data.length === 1) {
        return <Navigate to={`/collection/${collections.data[0].id}`} />
    } else if (collections.status === "FINISHED" && collections.data.length > 1) {
        return <SelectCollectionModal availableCollections={collections.data} />
    } else if (collections.status === "FAILED") {
        return <AlertBox icon={true} title={t("collections-view:load-error-title")} status="error" />
    } else {
        return <Loader title={t("collections-view:loader-title")} />
    }
}

const mapStateToProps = (state: AppState) => ({
    collections: state.collections.availableCollections,
    preferredCollection: state.collections.getPreferredCollectionResult,
    removePreferredCollectionResult: state.collections.removePreferredCollectionResult,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchCollections: () => dispatch(listCollectionsAction.started(true)),
    getPreferredCollection: () => dispatch(getPreferredCollectionIdAction.started()),
    removePreferredCollection: () => dispatch(removePreferredCollectionIdAction.started()),
    cleanCollectionsData: () => dispatch(resetAvailableCollectionsListAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(CollectionsContainer))
