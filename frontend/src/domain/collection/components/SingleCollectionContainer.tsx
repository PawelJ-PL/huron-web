import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { RouteComponentProps, withRouter } from "react-router"
import { Dispatch } from "redux"
import AlertBox from "../../../application/components/common/AlertBox"
import Loader from "../../../application/components/common/Loader"
import { AppState } from "../../../application/store"
import {
    cleanCollectionDetailsAction,
    getCollectionDetailsAction,
    removePreferredCollectionIdAction,
    setActiveCollectionAction,
    setPreferredCollectionIdAction,
} from "../store/Actions"

type Props = Pick<RouteComponentProps<{ collectionId: string }>, "match" | "history"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps> &
    Pick<WithTranslation, "t">

export const SingleCollectionContainer: React.FC<Props> = ({
    match: {
        params: { collectionId },
    },
    history,
    fetchCollectionData,
    fetchCollectionResult,
    setPreferredCollection,
    removePreferredCollection,
    resetCollectionData,
    setActiveCollection,
    removeActiveCollection,
    t,
}) => {
    useEffect(() => {
        if (
            fetchCollectionResult.status === "NOT_STARTED" ||
            fetchCollectionResult.status === "FAILED" ||
            fetchCollectionResult.params !== collectionId
        ) {
            fetchCollectionData(collectionId)
        }
        return () => {
            resetCollectionData()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (fetchCollectionResult.status === "FINISHED" && fetchCollectionResult.params === collectionId) {
            if (fetchCollectionResult.data) {
                setActiveCollection(collectionId)
                setPreferredCollection(collectionId)
            } else {
                removePreferredCollection()
                removeActiveCollection()
                history.push("/")
            }
        }
    }, [
        collectionId,
        fetchCollectionResult,
        setPreferredCollection,
        removePreferredCollection,
        history,
        setActiveCollection,
        removeActiveCollection,
    ])

    if (
        fetchCollectionResult.status === "FINISHED" &&
        fetchCollectionResult.data &&
        fetchCollectionResult.params === collectionId
    ) {
        return <div data-testid="TEMPORARY-COLLECTION-VIEW">{JSON.stringify(fetchCollectionResult.data)}</div>
    } else if (fetchCollectionResult.status === "FAILED") {
        return <AlertBox icon={true} title={t("single-collection-view:fetching-details-error-message")} status="error" />
    } else {
        return <Loader title={t("single-collection-view:loading-collection-data")} />
    }
}

const mapStateToProps = (state: AppState) => ({
    fetchCollectionResult: state.collections.collectionDetails,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchCollectionData: (collectionId: string) => dispatch(getCollectionDetailsAction.started(collectionId)),
    setPreferredCollection: (collectionId: string) => dispatch(setPreferredCollectionIdAction.started(collectionId)),
    removePreferredCollection: () => dispatch(removePreferredCollectionIdAction.started()),
    resetCollectionData: () => dispatch(cleanCollectionDetailsAction()),
    setActiveCollection: (collectionId: string) => dispatch(setActiveCollectionAction(collectionId)),
    removeActiveCollection: () => dispatch(setActiveCollectionAction(null)),
})

export default withRouter(connect(mapStateToProps, mapDispatchToProps)(withTranslation()(SingleCollectionContainer)))
