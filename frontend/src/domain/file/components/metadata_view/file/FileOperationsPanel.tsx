import { Button, ButtonGroup } from "@chakra-ui/button"
import { useDisclosure } from "@chakra-ui/hooks"
import { Box, Wrap, WrapItem } from "@chakra-ui/layout"
import capitalize from "lodash/capitalize"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { FaFileDownload } from "react-icons/fa"
import { FaFileUpload } from "react-icons/fa"
import { FaEdit } from "react-icons/fa"
import { FaTrashAlt } from "react-icons/fa"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import AlertBox from "../../../../../application/components/common/AlertBox"
import LoadingOverlay from "../../../../../application/components/common/LoadingOverlay"
import { AppState } from "../../../../../application/store"
import { EncryptionKey } from "../../../../collection/types/EncryptionKey"
import {
    deleteFilesRequestAction,
    downloadAndDecryptFileAction,
    renameRequestAction,
    resetDeleteFilesResultAction,
    resetDownloadFileResultAction,
    resetEncryptAndUpdateVersionStatusAction,
    resetRenameResultAction,
} from "../../../store/Actions"
import { FileDeleteFailed } from "../../../types/errors"
import { FileMetadata } from "../../../types/FilesystemUnitMetadata"
import DeleteFilesConfirmation from "../directory/DeleteFilesConfirmation"
import RenameModal from "../directory/RenameModal"
import UploadFileModal from "../directory/UploadFileModal"
import FileSaver from "file-saver"
import { useNavigate } from "react-router-dom"

type Props = {
    file: FileMetadata
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

export const FileOperationsPanel: React.FC<Props> = ({
    t,
    encryptionKeyResult,
    file,
    renameFile,
    resetAllActionsResults,
    actionInProgress,
    deleteFile,
    deleteResult,
    downloadFile,
    fileDownloadResult,
    resetDownloadResult,
}) => {
    const navigate = useNavigate()

    useEffect(() => {
        resetAllActionsResults()
        return () => {
            resetAllActionsResults()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (
            deleteResult.status === "FINISHED" &&
            deleteResult.params.collectionId === file.collectionId &&
            deleteResult.params.fileIds.includes(file.id)
        ) {
            navigate(`/collection/${file.collectionId}`)
        }

        if (deleteResult.status === "FAILED" && deleteResult.params.collectionId === file.collectionId) {
            if (deleteResult.error instanceof FileDeleteFailed && deleteResult.error.deleted.includes(file.id)) {
                navigate(`/collection/${file.collectionId}`)
            }
        }
    }, [deleteResult, file, navigate])

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

    const keyUnlocked = encryptionKeyResult.status === "FINISHED"

    const uploadDisclosure = useDisclosure()

    return (
        <Box>
            <LoadingOverlay active={actionInProgress} text={capitalize(t("common:action-in-progress"))} />
            <RenameModal />
            <DeleteFilesConfirmation />
            <UploadFileModal
                isOpen={uploadDisclosure.isOpen}
                onClose={uploadDisclosure.onClose}
                uploadData={{ type: "NewVersion", fileMetadata: file }}
            />
            {!keyUnlocked && (
                <AlertBox
                    title={t("file-view:file-data.download-upload-unavailable")}
                    status="warning"
                    alertProps={{ marginBottom: "0.5rem" }}
                />
            )}
            <ButtonGroup size="sm" colorScheme="brand">
                <Wrap justify="center">
                    <WrapItem>
                        <Button
                            leftIcon={<FaFileDownload />}
                            isDisabled={!keyUnlocked}
                            onClick={() =>
                                encryptionKeyResult.status === "FINISHED"
                                    ? downloadFile(file, encryptionKeyResult.data)
                                    : void 0
                            }
                        >
                            {capitalize(t("file-view:directory-content-list.file-actions.download-file"))}
                        </Button>
                    </WrapItem>
                    <WrapItem>
                        <Button leftIcon={<FaFileUpload />} isDisabled={!keyUnlocked} onClick={uploadDisclosure.onOpen}>
                            {capitalize(t("file-view:directory-content-list.file-actions.upload-new-version"))}
                        </Button>
                    </WrapItem>
                    <WrapItem>
                        <Button leftIcon={<FaEdit />} onClick={() => renameFile(file)}>
                            {capitalize(t("file-view:directory-content-list.file-actions.rename"))}
                        </Button>
                    </WrapItem>
                    <WrapItem>
                        <Button colorScheme="red" leftIcon={<FaTrashAlt />} onClick={() => deleteFile(file)}>
                            {capitalize(t("common:delete-imperative"))}
                        </Button>
                    </WrapItem>
                </Wrap>
            </ButtonGroup>
        </Box>
    )
}

const mapStateToProps = (state: AppState) => ({
    actionInProgress:
        state.files.renameResult.status === "PENDING" ||
        state.files.deleteFilesResult.status === "PENDING" ||
        state.files.versionUpdateResult.status === "PENDING" ||
        state.files.downloadFileResult.status === "PENDING",
    encryptionKeyResult: state.collections.encryptionKey,
    deleteResult: state.files.deleteFilesResult,
    fileDownloadResult: state.files.downloadFileResult,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    resetAllActionsResults: () => {
        dispatch(resetRenameResultAction())
        dispatch(resetDeleteFilesResultAction())
        dispatch(resetEncryptAndUpdateVersionStatusAction())
        dispatch(resetDownloadFileResultAction())
    },
    renameFile: (file: FileMetadata) => dispatch(renameRequestAction(file)),
    deleteFile: (file: FileMetadata) => dispatch(deleteFilesRequestAction([file])),
    downloadFile: (file: FileMetadata, encryptionKey: EncryptionKey) =>
        dispatch(
            downloadAndDecryptFileAction.started({
                collectionId: file.collectionId,
                fileId: file.id,
                encryptionKey: encryptionKey,
            })
        ),
    resetDownloadResult: () => dispatch(resetDownloadFileResultAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(FileOperationsPanel))
