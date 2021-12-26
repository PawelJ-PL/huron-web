import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
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
import { useParams } from "react-router-dom"
import { fetchMultipleUsersPublicDataAction } from "../../user/store/Actions"
import { isFileTree } from "../types/ObjectTree"
import { FileMetadata } from "../types/FilesystemUnitMetadata"

type Props = {
    collection: Collection
} & ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps> &
    Pick<WithTranslation, "t">

export const CollectionFilesContainer: React.FC<Props> = ({
    collection,
    fileTreeResult,
    fetchTree,
    resetTree,
    t,
    fetchAuthors,
    knownUsers,
}) => {
    const routeParams = useParams<{ collectionId: string; fileId?: string }>()
    const maybeFileId = routeParams.fileId

    useEffect(() => {
        return () => {
            resetTree()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        fetchTree(collection.id, maybeFileId ?? null)
    }, [maybeFileId, fetchTree, collection])

    useEffect(() => {
        if (fileTreeResult.status === "FINISHED" && !isFileTree(fileTreeResult.data)) {
            const authorIds = fileTreeResult.data.children
                .filter((child): child is FileMetadata => child["@type"] === "FileData")
                .map((f) => f.versionAuthor)
                .filter((author): author is string => author !== undefined && author !== null)
            const uniqueAuthorIds = Array.from(new Set(authorIds))
            const unknownUsers = uniqueAuthorIds.filter((id) => !Object.keys(knownUsers).includes(id))
            if (unknownUsers.length > 0) {
                fetchAuthors(unknownUsers)
            }
        }
    }, [fileTreeResult, knownUsers, fetchAuthors])

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
    knownUsers: state.users.knownUsers,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchTree: (collectionId: string, fileId: string | null) =>
        dispatch(fetchObjectTreeAction.started({ collectionId, objectId: fileId })),
    resetTree: () => dispatch(resetCurrentObjectTreeAction()),
    fetchAuthors: (authorIds: string[]) => dispatch(fetchMultipleUsersPublicDataAction.started(authorIds)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(CollectionFilesContainer))
