import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { RouteComponentProps, withRouter } from "react-router"
import { Dispatch } from "redux"
import { AppState } from "../../../application/store"
import { Collection } from "../../collection/types/Collection"
import { FileNotFound } from "../api/errors"
import { fetchObjectTreeAction, resetCurrentObjectTreeAction } from "../store/Actions"
import { RiFileForbidLine } from "react-icons/ri"
import EmptyPlaceholder from "../../../application/components/common/EmptyPlaceholder"
import Loader from "../../../application/components/common/Loader"
import SingleObjectView from "./metadata_view/SingleObjectView"
import UnexpectedErrorMessage from "../../../application/components/common/UnexpectedErrorMessage"

type Props = {
    collection: Collection
} & RouteComponentProps<{ fileId?: string }> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps> &
    Pick<WithTranslation, "t">

export const CollectionFilesContainer: React.FC<Props> = ({
    collection,
    match,
    fileTreeResult,
    fetchTree,
    resetTree,
    t,
}) => {
    const maybeFileId = match.params.fileId

    useEffect(() => {
        fetchTree(collection.id, maybeFileId ?? null)

        return () => {
            resetTree()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const renderError = (error: Error) => {
        if (error instanceof FileNotFound) {
            return <EmptyPlaceholder icon={RiFileForbidLine} text={t("file-view:file-not-found-error")} />
        } else {
            return <UnexpectedErrorMessage error={error} />
        }
    }

    if (fileTreeResult.status === "FAILED") {
        return renderError(fileTreeResult.error)
    } else if (fileTreeResult.status === "FINISHED") {
        return <SingleObjectView collection={collection} objectTree={fileTreeResult.data} />
    } else {
        return <Loader title={t("file-view:loader-title")} />
    }
}

const mapStateToProps = (state: AppState) => ({
    fileTreeResult: state.files.currentObjectTree,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchTree: (collectionId: string, fileId: string | null) =>
        dispatch(fetchObjectTreeAction.started({ collectionId, objectId: fileId })),
    resetTree: () => dispatch(resetCurrentObjectTreeAction()),
})

export default withRouter(connect(mapStateToProps, mapDispatchToProps)(withTranslation()(CollectionFilesContainer)))