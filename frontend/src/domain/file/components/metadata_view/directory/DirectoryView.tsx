import React, { useEffect } from "react"
import { FilesystemUnitMetadata } from "../../../types/FilesystemUnitMetadata"
import { FaFileExcel } from "react-icons/fa"
import { FaRegFolderOpen } from "react-icons/fa"
import { FaRegFile } from "react-icons/fa"
import { FaFileImage } from "react-icons/fa"
import { FaFilePdf } from "react-icons/fa"
import { FaFileWord } from "react-icons/fa"
import { IconType } from "react-icons/lib"
import EmptyPlaceholder from "../../../../../application/components/common/EmptyPlaceholder"
import { Box } from "@chakra-ui/layout"
import { WithTranslation, withTranslation } from "react-i18next"
import CompactDirectoryList from "./CompactDirectoryList"
import { useBreakpointValue } from "@chakra-ui/media-query"
import FullDirectoryList from "./FullDirectoryList"
import ActionsPanel from "./ActionsPanel"
import RenameModal from "./RenameModal"
import { FileAlreadyExists, FileContentNotChanged, RecursivelyDelete } from "../../../api/errors"
import AlertBox from "../../../../../application/components/common/AlertBox"
import { AppState } from "../../../../../application/store"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import {
    requestVersionUpdateAction,
    resetDeleteFilesResultAction,
    resetDownloadFileResultAction,
    resetEncryptAndUpdateVersionStatusAction,
    resetRenameResultAction,
} from "../../../store/Actions"
import DeleteFilesConfirmation from "./DeleteFilesConfirmation"
import { FileDeleteFailed } from "../../../types/errors"
import UploadFileModal from "./UploadFileModal"
import LoadingOverlay from "../../../../../application/components/common/LoadingOverlay"
import FileSaver from "file-saver"
import UnexpectedErrorMessage from "../../../../../application/components/common/UnexpectedErrorMessage"

type Props = {
    childObjects: FilesystemUnitMetadata[]
    collectionId: string
    thisDirectoryId: string | null
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

export const DirectoryView: React.FC<Props> = ({
    childObjects,
    collectionId,
    t,
    thisDirectoryId,
    renameResult,
    resetRenameResult,
    deleteResult,
    resetDeleteResult,
    requestedVersionUpdate,
    resetVersionUpdateRequest,
    versionUpdateResult,
    resetVersionUpdateResult,
    fileDownloadResult,
    resetDownloadResult,
}) => {
    useEffect(() => {
        resetRenameResult()
        resetDeleteResult()
        resetVersionUpdateRequest()
        resetVersionUpdateResult()
        resetDownloadResult()
        return () => {
            resetRenameResult()
            resetDeleteResult()
            resetVersionUpdateRequest()
            resetVersionUpdateResult()
            resetDownloadResult()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (fileDownloadResult.status === "FINISHED") {
            const blob = new Blob([fileDownloadResult.data.data], {
                type: fileDownloadResult.data.mimeType ?? undefined,
            })
            const name = fileDownloadResult.data.name
            resetDownloadResult()
            FileSaver.saveAs(blob, name)
        }
    }, [resetDownloadResult, fileDownloadResult])

    const fullSize = useBreakpointValue({ base: false, lg: true })

    const renderError = (error: Error, onClose?: () => void) => {
        let extraProps: { title: string; status: "warning" | "error" } | undefined = undefined

        if (error instanceof FileAlreadyExists) {
            extraProps = { title: t("file-view:directory-content-list.file-already-exists"), status: "warning" }
        } else if (
            error instanceof FileDeleteFailed &&
            error.errors.some((e) => e.error instanceof RecursivelyDelete)
        ) {
            extraProps = {
                title: t("file-view:directory-content-list.delete-recursively-not-allowed-error"),
                status: "warning",
            }
        } else if (error instanceof FileContentNotChanged) {
            extraProps = {
                title: t("file-view:directory-content-list.file-content-not-changed-error"),
                status: "warning",
            }
        }

        if (extraProps) {
            return <AlertBox icon={true} onClose={onClose} alertProps={{ marginY: "0.2rem" }} {...extraProps} />
        } else {
            return <UnexpectedErrorMessage error={error} onClose={onClose} alertProps={{ marginY: "0.2rem" }} />
        }
    }

    return (
        <Box>
            <RenameModal />
            <DeleteFilesConfirmation />
            <LoadingOverlay active={versionUpdateResult.status === "PENDING"} text={t("common:action-in-progress")} />
            {requestedVersionUpdate !== null && (
                <UploadFileModal
                    isOpen={true}
                    onClose={resetVersionUpdateRequest}
                    uploadData={{ type: "NewVersion", fileMetadata: requestedVersionUpdate }}
                />
            )}
            {renameResult.status === "FAILED" && renderError(renameResult.error, resetRenameResult)}
            {deleteResult.status === "FAILED" && renderError(deleteResult.error, resetDeleteResult)}
            {versionUpdateResult.status === "FAILED" &&
                renderError(versionUpdateResult.error, resetVersionUpdateResult)}
            {fileDownloadResult.status === "FAILED" && renderError(fileDownloadResult.error, resetDownloadResult)}
            <LoadingOverlay
                active={fileDownloadResult.status === "PENDING"}
                text={t("file-view:directory-content-list.download-in-progress-message")}
            />
            <ActionsPanel collectionId={collectionId} thisDirectoryId={thisDirectoryId} />
            {fullSize ? (
                <FullDirectoryList childObjects={childObjects} collectionId={collectionId} />
            ) : (
                <CompactDirectoryList childObjects={childObjects} collectionId={collectionId} />
            )}
            {childObjects.length < 1 && (
                <EmptyPlaceholder
                    icon={FaRegFolderOpen}
                    text={t("file-view:directory-content-list.empty-directory-placeholder")}
                />
            )}
        </Box>
    )
}

export function iconForFileType(fileType: string): IconType {
    const typeIconMapping: Record<string, IconType> = {
        directory: FaRegFolderOpen,
        "image/jpeg": FaFileImage,
        "image/bmp": FaFileImage,
        "image/gif": FaFileImage,
        "image/png": FaFileImage,
        "application/pdf": FaFilePdf,
        "application/msword": FaFileWord,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document": FaFileWord,
        "application/vnd.oasis.opendocument.text": FaFileWord,
        "application/vnd.oasis.opendocument.spreadsheet": FaFileExcel,
        "application/vnd.ms-excel": FaFileExcel,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": FaFileExcel,
    }
    return typeIconMapping[fileType] ?? FaRegFile
}

const mapStateToProps = (state: AppState) => ({
    renameResult: state.files.renameResult,
    deleteResult: state.files.deleteFilesResult,
    requestedVersionUpdate: state.files.requestedVersionUpdate,
    versionUpdateResult: state.files.versionUpdateResult,
    fileDownloadResult: state.files.downloadFileResult,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    resetRenameResult: () => dispatch(resetRenameResultAction()),
    resetDeleteResult: () => dispatch(resetDeleteFilesResultAction()),
    resetVersionUpdateRequest: () => dispatch(requestVersionUpdateAction(null)),
    resetVersionUpdateResult: () => dispatch(resetEncryptAndUpdateVersionStatusAction()),
    resetDownloadResult: () => dispatch(resetDownloadFileResultAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(DirectoryView))
