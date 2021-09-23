import { Box, Heading } from "@chakra-ui/layout"
import { Table, TableCaption, Tbody, Td, Tr } from "@chakra-ui/table"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import Loader from "../../../../../application/components/common/Loader"
import UnexpectedErrorMessage from "../../../../../application/components/common/UnexpectedErrorMessage"
import { AppState } from "../../../../../application/store"
import {
    deleteVersionRequestAction,
    downloadAndDecryptFileAction,
    listFileVersionsAction,
} from "../../../store/Actions"
import { FileVersion } from "../../../types/FileVersion"
import { formatDate, formatFileSize } from "../../fieldFormatters"
import Author from "./Author"
import { FaFileDownload } from "react-icons/fa"
import { FaTrashAlt } from "react-icons/fa"
import { IconButton } from "@chakra-ui/button"
import { EncryptionKey } from "../../../../collection/types/EncryptionKey"
import DeleteVersionModal from "./DeleteVersionModal"
import LoadingOverlay from "../../../../../application/components/common/LoadingOverlay"
import capitalize from "lodash/capitalize"
import { DELETE_VERSION_BUTTON, DOWNLOAD_VERSION_BUTTON } from "../../testids"

type Props = {
    collectionId: string
    fileId: string
} & ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps> &
    Pick<WithTranslation, "t" | "i18n">

export const VersionsList: React.FC<Props> = ({
    t,
    i18n,
    collectionId,
    fileId,
    versionsResult,
    fetchVersions,
    encryptionKey,
    downloadVersion,
    requestVersionDelete,
    deleteResult,
}) => {
    useEffect(() => {
        if (
            (versionsResult.status === "FINISHED" || versionsResult.status === "PENDING") &&
            versionsResult.params.collectionId === collectionId &&
            versionsResult.params.fileId === fileId
        ) {
            return
        } else {
            fetchVersions(collectionId, fileId)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const renderEntry = (entry: FileVersion) => (
        <Tr key={entry.versionId}>
            <Td>{formatDate(i18n, entry.updatedAt)}</Td>
            <Td>
                <Author authorId={entry.versionAuthor ?? undefined} />
            </Td>
            <Td>{formatFileSize(i18n, entry.encryptedSize)}</Td>
            <Td>
                <IconButton
                    aria-label={t("file-view:file-data.version-actions.download")}
                    icon={<FaFileDownload />}
                    colorScheme="brand"
                    variant="ghost"
                    isDisabled={encryptionKey.status !== "FINISHED"}
                    onClick={() =>
                        encryptionKey.status === "FINISHED"
                            ? downloadVersion(entry.collectionId, entry.id, entry.versionId, encryptionKey.data)
                            : void 0
                    }
                    data-testid={`${DOWNLOAD_VERSION_BUTTON}_${entry.versionId}`}
                />
                <IconButton
                    aria-label={t("file-view:file-data.version-actions.delete")}
                    icon={<FaTrashAlt />}
                    colorScheme="red"
                    variant="ghost"
                    onClick={() => requestVersionDelete(entry)}
                    data-testid={`${DELETE_VERSION_BUTTON}_${entry.versionId}`}
                />
            </Td>
        </Tr>
    )

    if (versionsResult.status === "NOT_STARTED" || versionsResult.status === "PENDING") {
        return <Loader title={t("file-view:file-data.loading-versions")} />
    } else if (versionsResult.status === "FAILED") {
        return <UnexpectedErrorMessage error={versionsResult.error} />
    } else {
        return (
            <Box>
                <DeleteVersionModal />
                <LoadingOverlay
                    active={deleteResult.status === "PENDING"}
                    text={capitalize(t("common:action-in-progress"))}
                />
                <Table size="sm">
                    <TableCaption placement="top">
                        <Heading as="h6" size="xs">
                            {t("file-view:file-data.previous-versions")}
                        </Heading>
                    </TableCaption>
                    <Tbody>{versionsResult.data.slice(1).map(renderEntry)}</Tbody>
                </Table>
            </Box>
        )
    }
}

const mapStateToProps = (state: AppState) => ({
    versionsResult: state.files.fileVersionsResult,
    encryptionKey: state.collections.encryptionKey,
    deleteResult: state.files.deleteVersionResult,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchVersions: (collectionId: string, fileId: string) =>
        dispatch(listFileVersionsAction.started({ collectionId, fileId })),
    downloadVersion: (collectionId: string, fileId: string, versionId: string, encryptionKey: EncryptionKey) =>
        dispatch(downloadAndDecryptFileAction.started({ collectionId, fileId, versionId, encryptionKey })),
    requestVersionDelete: (version: FileVersion) => dispatch(deleteVersionRequestAction(version)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(VersionsList))
