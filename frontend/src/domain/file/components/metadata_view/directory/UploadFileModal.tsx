import { Modal, ModalBody, ModalContent, ModalFooter, ModalOverlay } from "@chakra-ui/react"
import React, { useState } from "react"
import { FileError, useDropzone } from "react-dropzone"
import { Box } from "@chakra-ui/layout"
import EmptyPlaceholder from "../../../../../application/components/common/EmptyPlaceholder"
import { GoCloudUpload } from "react-icons/go"
import { WithTranslation, withTranslation } from "react-i18next"
import { Button } from "@chakra-ui/button"
import capitalize from "lodash/capitalize"
import AlertBox from "../../../../../application/components/common/AlertBox"
import { AppState } from "../../../../../application/store"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { encryptAndUpdateVersionAction, encryptAndUploadFileAction } from "../../../store/Actions"
import { EncryptionKey } from "../../../../collection/types/EncryptionKey"
import { formatFileSize } from "../../fieldFormatters"
import { FileMetadata } from "../../../types/FilesystemUnitMetadata"
import { UPLOAD_FILE_INPUT } from "../../testids"

export const MAX_FILE_SIZE = 10485760

type UploadNewFileData = {
    type: "NewFile"
    collectionId: string
    parent: string | null
}

type UploadNewVersionData = {
    type: "NewVersion"
    fileMetadata: FileMetadata
}

type Props = {
    isOpen: boolean
    onClose: () => void
    uploadData: UploadNewFileData | UploadNewVersionData
} & Pick<WithTranslation, "t" | "i18n"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

export const UploadFileModal: React.FC<Props> = ({
    isOpen,
    onClose,
    t,
    encryptionKey,
    encryptAndUploadNewFile,
    uploadData,
    i18n,
    encryptAndUpdateVersion,
}) => {
    const [file, setFile] = useState<File | null>(null)

    const [rejectionError, setRejectionError] = useState<FileError | null>(null)

    const { getRootProps, getInputProps } = useDropzone({
        maxFiles: 1,
        multiple: false,
        maxSize: MAX_FILE_SIZE,
        onDropAccepted: (files) => {
            setRejectionError(null)
            setFile(files[0])
        },
        onDropRejected: (rejections) => {
            setFile(null)
            setRejectionError(rejections[0].errors[0])
        },
    })

    const handleClose = () => {
        setFile(null)
        setRejectionError(null)
        onClose()
    }

    const errorMessage = (error: FileError) => {
        switch (error.code) {
            case "file-too-large":
                return t("file-view:directory-content-list.file-rejection-errors.file-too-large", {
                    maxSize: formatFileSize(i18n, MAX_FILE_SIZE),
                })
            case "too-many-files":
                return t("file-view:directory-content-list.file-rejection-errors.too-many-files")
            default:
                return error.message
        }
    }

    const handleUpload = (fileToUpload: File, encryptionKeyValue: EncryptionKey) => {
        if (uploadData.type === "NewFile") {
            encryptAndUploadNewFile(uploadData.collectionId, uploadData.parent, fileToUpload, encryptionKeyValue)
        } else {
            encryptAndUpdateVersion(uploadData.fileMetadata, fileToUpload, encryptionKeyValue)
        }
    }

    return (
        <Modal isOpen={isOpen} onClose={handleClose}>
            <ModalOverlay />
            <ModalContent>
                <ModalBody>
                    {encryptionKey.status === "FINISHED" && (
                        <Box {...getRootProps()} width="100%" borderWidth="2px" borderRadius="2px" borderStyle="dashed">
                            <EmptyPlaceholder
                                text={t("file-view:directory-content-list.dropzone-content")}
                                icon={GoCloudUpload}
                            />
                            <input {...getInputProps()} data-testid={UPLOAD_FILE_INPUT} />
                        </Box>
                    )}
                    {rejectionError && (
                        <AlertBox
                            status="warning"
                            title={t("file-view:directory-content-list.load-file-error")}
                            description={errorMessage(rejectionError)}
                            onClose={() => setRejectionError(null)}
                            alertProps={{
                                flexDirection: "column",
                                marginTop: "0.3rem",
                                alignItems: "center",
                                justifyContent: "center",
                                textAlign: "center",
                            }}
                        />
                    )}
                    {encryptionKey.status !== "FINISHED" && (
                        <AlertBox
                            status="warning"
                            title={t("file-view:directory-content-list.missing-encryption-key-message")}
                            icon={true}
                            alertProps={{ marginTop: "0.3rem" }}
                        />
                    )}
                    {file && (
                        <AlertBox status="info" icon={false} title={file.name} alertProps={{ marginTop: "0.3rem" }} />
                    )}
                </ModalBody>

                <ModalFooter>
                    <Button size="xs" onClick={handleClose} marginRight="0.2em" colorScheme="gray2">
                        {capitalize(t("common:cancel-imperative"))}
                    </Button>
                    <Button
                        size="xs"
                        marginLeft="0.2em"
                        colorScheme="brand"
                        disabled={file === null}
                        onClick={() => {
                            handleClose()
                            file && encryptionKey.status === "FINISHED"
                                ? handleUpload(file, encryptionKey.data)
                                : void 0
                        }}
                    >
                        {t("file-view:directory-content-list.encrypt-and-send")}
                    </Button>
                </ModalFooter>
            </ModalContent>
        </Modal>
    )
}

const mapStateToProps = (state: AppState) => ({
    encryptionKey: state.collections.encryptionKey,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    encryptAndUploadNewFile: (collectionId: string, parent: string | null, file: File, encryptionKey: EncryptionKey) =>
        dispatch(encryptAndUploadFileAction.started({ collectionId, parent, file, encryptionKey })),
    encryptAndUpdateVersion: (fileMetadata: FileMetadata, file: File, encryptionKey: EncryptionKey) =>
        dispatch(
            encryptAndUpdateVersionAction.started({
                collectionId: fileMetadata.collectionId,
                fileId: fileMetadata.id,
                latestVersionDigest: fileMetadata.contentDigest,
                file,
                encryptionKey,
            })
        ),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(UploadFileModal))
