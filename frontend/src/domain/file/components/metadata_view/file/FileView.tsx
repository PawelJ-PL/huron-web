import Icon from "@chakra-ui/icon"
import { Box, Center, Divider, Flex, Heading, Text } from "@chakra-ui/layout"
import React, { useEffect } from "react"
import { FileMetadata } from "../../../types/FilesystemUnitMetadata"
import { iconForFileType } from "../directory/DirectoryView"
import isString from "lodash/isString"
import { formatDate, formatFileSize } from "../../fieldFormatters"
import { withTranslation, WithTranslation } from "react-i18next"
import Author from "./Author"
import FileOperationsPanel from "./FileOperationsPanel"
import { FileAlreadyExists, FileContentNotChanged } from "../../../api/errors"
import AlertBox from "../../../../../application/components/common/AlertBox"
import UnexpectedErrorMessage from "../../../../../application/components/common/UnexpectedErrorMessage"
import { AppState } from "../../../../../application/store"
import { Dispatch } from "redux"
import {
    resetDeleteFilesResultAction,
    resetDeleteVersionResultAction,
    resetDownloadFileResultAction,
    resetEncryptAndUpdateVersionStatusAction,
    resetRenameResultAction,
} from "../../../store/Actions"
import { connect } from "react-redux"
import VersionsList from "./VersionsList"

type Props = {
    metadata: FileMetadata
} & Pick<WithTranslation, "i18n" | "t"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

export const FileView: React.FC<Props> = ({
    metadata,
    i18n,
    t,
    deleteResult,
    resetDeleteResult,
    renameResult,
    resetRenameResult,
    updateResult,
    resetUpdateResult,
    downloadResult,
    resetDownloadResult,
    deleteVersionResult,
    resetDeleteVersionResult,
    resetAll,
}) => {
    useEffect(() => {
        resetAll()
        return () => {
            resetAll()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const FileIcon = iconForFileType(metadata.mimeType ?? "unknown")

    const renderError = (error: Error, onClose: () => void) => {
        let title: string | undefined = undefined

        if (error instanceof FileAlreadyExists) {
            title = t("file-view:directory-content-list.file-already-exists")
        } else if (error instanceof FileContentNotChanged) {
            title = t("file-view:directory-content-list.file-content-not-changed-error")
        }

        return title ? (
            <AlertBox
                icon={true}
                title={title}
                status="warning"
                onClose={onClose}
                alertProps={{ marginBottom: "0.5rem" }}
            />
        ) : (
            <UnexpectedErrorMessage error={error} onClose={onClose} alertProps={{ marginBottom: "0.5rem" }} />
        )
    }

    return (
        <Box marginX="1.5rem">
            {deleteResult.status === "FAILED" && renderError(deleteResult.error, resetDeleteResult)}
            {renameResult.status === "FAILED" && renderError(renameResult.error, resetRenameResult)}
            {updateResult.status === "FAILED" && renderError(updateResult.error, resetUpdateResult)}
            {downloadResult.status === "FAILED" && renderError(downloadResult.error, resetDownloadResult)}
            {deleteVersionResult.status === "FAILED" &&
                renderError(deleteVersionResult.error, resetDeleteVersionResult)}
            <Center flexDirection="column">
                <Icon as={FileIcon} boxSize="5rem" color="brand.500" />
                <Flex
                    direction="column"
                    justifyContent="center"
                    wordBreak="break-all"
                    maxWidth="80%"
                    alignItems="center"
                >
                    <Heading as="h3" size="lg">
                        {" "}
                        {metadata.name}
                    </Heading>
                    <Heading as="h5" size="sm" opacity="0.5">
                        {metadata.mimeType}
                    </Heading>
                </Flex>
                <Box minWidth="35%" marginBottom="1.5rem">
                    <Entry
                        value={formatFileSize(i18n, metadata.encryptedSize)}
                        name={t("file-view:file-data.attributes.encrypted-size")}
                    />
                    <Entry
                        value={formatDate(i18n, metadata.updatedAt)}
                        name={t("file-view:file-data.attributes.updated-at")}
                    />
                    <Entry
                        value={<Author authorId={metadata.versionAuthor ?? undefined} />}
                        name={t("file-view:file-data.attributes.author")}
                    />
                </Box>
                <FileOperationsPanel file={metadata} />
                <Box marginTop="0.5rem" minWidth="35%">
                    <VersionsList collectionId={metadata.collectionId} fileId={metadata.id} />
                </Box>
            </Center>
        </Box>
    )
}

type EntryProps = {
    value: string | JSX.Element
    name: string
}

const Entry: React.FC<EntryProps> = ({ value, name }) => (
    <Box marginTop="0.5rem">
        <Flex alignItems="center">
            <Heading
                as="h6"
                size="xs"
                opacity="0.6"
                display="flex"
                justifyContent="center"
                alignItems="center"
                marginRight="0.5ch"
            >
                {name}
            </Heading>
            {isString(value) ? (
                <Text lineHeight="1rem" marginLeft="auto" display="flex" justifyContent="center" alignItems="center">
                    {value}
                </Text>
            ) : (
                <Box marginLeft="auto" display="flex" justifyContent="center" alignItems="center">
                    {value}
                </Box>
            )}
        </Flex>
        <Divider />
    </Box>
)

const mapStateToProps = (state: AppState) => ({
    deleteResult: state.files.deleteFilesResult,
    renameResult: state.files.renameResult,
    updateResult: state.files.versionUpdateResult,
    downloadResult: state.files.downloadFileResult,
    deleteVersionResult: state.files.deleteVersionResult,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    resetAll: () => {
        dispatch(resetRenameResultAction())
        dispatch(resetDeleteFilesResultAction())
        dispatch(resetEncryptAndUpdateVersionStatusAction())
        dispatch(resetDownloadFileResultAction())
        dispatch(resetDeleteVersionResultAction())
    },
    resetRenameResult: () => dispatch(resetRenameResultAction()),
    resetDeleteResult: () => dispatch(resetDeleteFilesResultAction()),
    resetUpdateResult: () => dispatch(resetEncryptAndUpdateVersionStatusAction()),
    resetDownloadResult: () => dispatch(resetDownloadFileResultAction()),
    resetDeleteVersionResult: () => dispatch(resetDeleteVersionResultAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(FileView))
