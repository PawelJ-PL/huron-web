import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { useNavigate, useParams } from "react-router"
import { Dispatch } from "redux"
import AlertBox from "../../../application/components/common/AlertBox"
import Loader from "../../../application/components/common/Loader"
import { AppState } from "../../../application/store"
import CollectionFilesContainer from "../../file/components/CollectionFilesContainer"
import {
    getCollectionDetailsAction,
    removePreferredCollectionIdAction,
    resetRemovePreferredCollectionResultAction,
    setActiveCollectionAction,
    setPreferredCollectionIdAction,
} from "../store/Actions"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & Pick<WithTranslation, "t">

export const SingleCollectionContainer: React.FC<Props> = ({
    fetchCollectionData,
    fetchCollectionResult,
    setPreferredCollection,
    removePreferredCollection,
    setActiveCollection,
    removeActiveCollection,
    removePreferredCollectionResult,
    resetRemovePreferredCollectionResult,
    t,
}) => {
    const navigate = useNavigate()
    const { collectionId = "" } = useParams<{ collectionId: string }>()

    useEffect(() => {
        resetRemovePreferredCollectionResult()
        if (fetchCollectionResult.status === "NOT_STARTED" || fetchCollectionResult.status === "FAILED") {
            fetchCollectionData(collectionId)
        }
        return () => {
            // resetCollectionData()
            resetRemovePreferredCollectionResult()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (fetchCollectionResult.status !== "NOT_STARTED" && fetchCollectionResult.params !== collectionId) {
            fetchCollectionData(collectionId)
        }
    }, [collectionId, fetchCollectionData, fetchCollectionResult])

    useEffect(() => {
        if (fetchCollectionResult.status === "FINISHED" && fetchCollectionResult.params === collectionId) {
            if (fetchCollectionResult.data) {
                setActiveCollection(collectionId)
                setPreferredCollection(collectionId)
            } else {
                removePreferredCollection()
                removeActiveCollection()
            }
        }
    }, [
        collectionId,
        fetchCollectionResult,
        setPreferredCollection,
        removePreferredCollection,
        setActiveCollection,
        removeActiveCollection,
    ])

    useEffect(() => {
        if (removePreferredCollectionResult.status === "FINISHED") {
            navigate("/")
        }
    }, [removePreferredCollectionResult, navigate])

    if (
        fetchCollectionResult.status === "FINISHED" &&
        fetchCollectionResult.data &&
        fetchCollectionResult.params === collectionId
    ) {
        return <CollectionFilesContainer collection={fetchCollectionResult.data} />
    } else if (fetchCollectionResult.status === "FAILED") {
        return (
            <AlertBox icon={true} title={t("single-collection-view:fetching-details-error-message")} status="error" />
        )
    } else {
        return <Loader title={t("single-collection-view:loading-collection-data")} />
    }
}

const mapStateToProps = (state: AppState) => ({
    fetchCollectionResult: state.collections.collectionDetails,
    removePreferredCollectionResult: state.collections.removePreferredCollectionResult,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchCollectionData: (collectionId: string) => dispatch(getCollectionDetailsAction.started(collectionId)),
    setPreferredCollection: (collectionId: string) => dispatch(setPreferredCollectionIdAction.started(collectionId)),
    removePreferredCollection: () => dispatch(removePreferredCollectionIdAction.started()),
    resetRemovePreferredCollectionResult: () => dispatch(resetRemovePreferredCollectionResultAction()),
    setActiveCollection: (collectionId: string) => dispatch(setActiveCollectionAction(collectionId)),
    removeActiveCollection: () => dispatch(setActiveCollectionAction(null)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(SingleCollectionContainer))
