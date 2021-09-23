import { Button } from "@chakra-ui/button"
import { useDisclosure } from "@chakra-ui/hooks"
import { Box, Wrap, WrapItem } from "@chakra-ui/layout"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { FaFolderPlus } from "react-icons/fa"
import { FaFileUpload } from "react-icons/fa"
import { connect } from "react-redux"
import AlertBox from "../../../../../application/components/common/AlertBox"
import LoadingOverlay from "../../../../../application/components/common/LoadingOverlay"
import { AppState } from "../../../../../application/store"
import NewDirectoryModal from "./NewDirectoryModal"
import capitalize from "lodash/capitalize"
import { Dispatch } from "redux"
import {
    deleteFilesRequestAction,
    resetCreateDirectoryStatusAction,
    resetEncryptAndUploadFileStatusAction,
} from "../../../store/Actions"
import { EncryptedFileTooLarge, FileAlreadyExists } from "../../../api/errors"
import UploadFileModal from "./UploadFileModal"
import { formatFileSize } from "../../fieldFormatters"
import { FaTrashAlt } from "react-icons/fa"
import { FilesystemUnitMetadata } from "../../../types/FilesystemUnitMetadata"
import UnexpectedErrorMessage from "../../../../../application/components/common/UnexpectedErrorMessage"

type Props = {
    collectionId: string
    thisDirectoryId: string | null
} & Pick<WithTranslation, "t" | "i18n"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

export const ActionsPanel: React.FC<Props> = ({
    t,
    collectionId,
    thisDirectoryId,
    actionInProgress,
    createDirResult,
    resetCreateDirResult,
    uploadFileResult,
    resetUploadFileResult,
    i18n,
    selectedFiles,
    requestFilesDelete,
}) => {
    const newDirDisclosure = useDisclosure()
    const newFileDisclosure = useDisclosure()

    useEffect(() => {
        return () => {
            resetCreateDirResult()
            resetUploadFileResult()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const renderError = (error: Error, onClose?: () => void) => {
        let extraProps: { title: string; status: "warning" | "error" } | undefined = undefined

        if (error instanceof FileAlreadyExists) {
            extraProps = { title: t("file-view:directory-content-list.file-already-exists"), status: "warning" }
        } else if (error instanceof EncryptedFileTooLarge) {
            extraProps = {
                title: t("file-view:directory-content-list.encrypted file to large", {
                    maxSize: formatFileSize(i18n, error.maxSize),
                    realSize: formatFileSize(i18n, error.realSize),
                }),
                status: "warning",
            }
        }

        const commonAlertProps = { marginY: "0.2rem" }

        return extraProps ? (
            <AlertBox icon={true} onClose={onClose} alertProps={commonAlertProps} {...extraProps} />
        ) : (
            <UnexpectedErrorMessage error={error} onClose={onClose} alertProps={commonAlertProps} />
        )
    }

    return (
        <Box>
            {createDirResult.status === "FAILED" && renderError(createDirResult.error, resetCreateDirResult)}
            {uploadFileResult.status === "FAILED" && renderError(uploadFileResult.error, resetUploadFileResult)}
            <LoadingOverlay active={actionInProgress} text={t("common:action-in-progress")} />
            <NewDirectoryModal
                isOpen={newDirDisclosure.isOpen}
                onClose={newDirDisclosure.onClose}
                collectionId={collectionId}
                parent={thisDirectoryId}
            />
            <UploadFileModal
                isOpen={newFileDisclosure.isOpen}
                onClose={newFileDisclosure.onClose}
                uploadData={{ type: "NewFile", collectionId, parent: thisDirectoryId }}
            />
            <Wrap spacing="0.3rem">
                <WrapItem>
                    <Button colorScheme="brand" size="sm" leftIcon={<FaFolderPlus />} onClick={newDirDisclosure.onOpen}>
                        {t("file-view:directory-content-list.create-directory")}
                    </Button>
                </WrapItem>
                <WrapItem>
                    <Button
                        colorScheme="brand"
                        size="sm"
                        leftIcon={<FaFileUpload />}
                        onClick={newFileDisclosure.onOpen}
                    >
                        {t("file-view:directory-content-list.upload-file-button-label")}
                    </Button>
                </WrapItem>
                <WrapItem>
                    <Button
                        colorScheme="red"
                        size="sm"
                        leftIcon={<FaTrashAlt />}
                        onClick={() => requestFilesDelete(selectedFiles)}
                        disabled={selectedFiles.length < 1}
                    >
                        {capitalize(t("common:delete-imperative"))}
                    </Button>
                </WrapItem>
            </Wrap>
        </Box>
    )
}

const mapStateToProps = (state: AppState) => ({
    actionInProgress: [state.files.createDirectoryResult.status, state.files.fileUploadResult.status].includes(
        "PENDING"
    ),
    createDirResult: state.files.createDirectoryResult,
    uploadFileResult: state.files.fileUploadResult,
    selectedFiles: Array.from(state.files.selectedFiles),
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    resetCreateDirResult: () => dispatch(resetCreateDirectoryStatusAction()),
    resetUploadFileResult: () => dispatch(resetEncryptAndUploadFileStatusAction()),
    requestFilesDelete: (files: FilesystemUnitMetadata[]) => dispatch(deleteFilesRequestAction(files)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(ActionsPanel))
