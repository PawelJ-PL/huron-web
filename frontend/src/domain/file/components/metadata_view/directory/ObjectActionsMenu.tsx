import { Button, IconButton } from "@chakra-ui/button"
import { Menu, MenuButton, MenuItem, MenuList } from "@chakra-ui/menu"
import React from "react"
import { FileMetadata, FilesystemUnitMetadata } from "../../../types/FilesystemUnitMetadata"
import { FaChevronDown } from "react-icons/fa"
import { BiMenu } from "react-icons/bi"
import { WithTranslation, withTranslation } from "react-i18next"
import { Dispatch } from "redux"
import {
    deleteFilesRequestAction,
    downloadAndDecryptFileAction,
    renameRequestAction,
    requestVersionUpdateAction,
} from "../../../store/Actions"
import { connect } from "react-redux"
import { AppState } from "../../../../../application/store"
import { EncryptionKey } from "../../../../collection/types/EncryptionKey"

type Props = {
    fullSize: boolean
    metadata: FilesystemUnitMetadata
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapDispatchToProps> &
    ReturnType<typeof mapStateToProps>

export const ObjectActionsMenu: React.FC<Props> = ({
    t,
    fullSize,
    metadata,
    requestRename,
    requestDelete,
    requestVersionUpdate,
    encryptionKeyResult,
    downloadFile,
}) => {
    const button = fullSize ? (
        <MenuButton as={Button} colorScheme="brand" rightIcon={<FaChevronDown />} size="sm">
            {t("file-view:directory-content-list.file-actions-label")}
        </MenuButton>
    ) : (
        <MenuButton
            as={IconButton}
            aria-label={t("file-view:directory-content-list.file-actions-label")}
            icon={<BiMenu />}
            variant="outline"
            size="sm"
        />
    )

    return (
        <Menu>
            {button}
            <MenuList>
                <MenuItem onClick={() => requestRename(metadata)}>
                    {t("file-view:directory-content-list.file-actions.rename")}
                </MenuItem>
                <MenuItem onClick={() => requestDelete(metadata)}>
                    {t("file-view:directory-content-list.file-actions.delete-file")}
                </MenuItem>
                {metadata["@type"] === "FileData" && (
                    <MenuItem onClick={() => requestVersionUpdate(metadata)}>
                        {t("file-view:directory-content-list.file-actions.upload-new-version")}
                    </MenuItem>
                )}
                {metadata["@type"] === "FileData" && (
                    <MenuItem
                        onClick={() =>
                            encryptionKeyResult.status === "FINISHED"
                                ? downloadFile(metadata, encryptionKeyResult.data)
                                : () => void 0
                        }
                        isDisabled={encryptionKeyResult.status !== "FINISHED"}
                    >
                        {t("file-view:directory-content-list.file-actions.download-file")}
                    </MenuItem>
                )}
            </MenuList>
        </Menu>
    )
}

const mapStateToProps = (state: AppState) => ({
    encryptionKeyResult: state.collections.encryptionKey,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    requestRename: (file: FilesystemUnitMetadata) => dispatch(renameRequestAction(file)),
    requestDelete: (file: FilesystemUnitMetadata) => dispatch(deleteFilesRequestAction([file])),
    requestVersionUpdate: (file: FileMetadata) => dispatch(requestVersionUpdateAction(file)),
    downloadFile: (file: FileMetadata, encryptionKey: EncryptionKey) =>
        dispatch(
            downloadAndDecryptFileAction.started({
                collectionId: file.collectionId,
                fileId: file.id,
                encryptionKey: encryptionKey,
            })
        ),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(ObjectActionsMenu))
