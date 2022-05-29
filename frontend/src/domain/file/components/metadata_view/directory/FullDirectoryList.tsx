import { Box, Flex, Link, Text } from "@chakra-ui/layout"
import { Table, Tbody, Td, Th, Thead, Tr } from "@chakra-ui/table"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { FilesystemUnitMetadata } from "../../../types/FilesystemUnitMetadata"
import { formatDate, formatFileSize } from "../../fieldFormatters"
import { iconForFileType } from "./DirectoryView"
import { Link as RouterLink } from "react-router-dom"
import ObjectActionsMenu from "./ObjectActionsMenu"
import { Checkbox } from "@chakra-ui/react"
import { AppState } from "../../../../../application/store"
import { Dispatch } from "redux"
import { selectFilesAction } from "../../../store/Actions"
import { connect } from "react-redux"
import { SELECT_ALL_FILES_CHECKBOX, SELECT_FILE_CHECKBOX } from "../../testids"
import Author from "../file/Author"

type Props = {
    childObjects: FilesystemUnitMetadata[]
    collectionId: string
} & Pick<WithTranslation, "t" | "i18n"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

export const FullDirectoryList: React.FC<Props> = ({
    childObjects,
    t,
    i18n,
    collectionId,
    selectedFiles,
    setSelectedFiles,
}) => {
    useEffect(() => {
        setSelectedFiles([])
        return () => {
            setSelectedFiles([])
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const areAllSelected = childObjects.length > 0 && childObjects.every((child) => selectedFiles.includes(child))

    const renderName = (fsUnit: FilesystemUnitMetadata) => {
        const FileIcon =
            fsUnit["@type"] === "DirectoryData"
                ? iconForFileType("directory")
                : iconForFileType(fsUnit.mimeType ?? "unknown")

        return (
            <Flex maxWidth="40vw">
                <Box marginRight="0.2rem">
                    <FileIcon />
                </Box>
                <Link color="text" as={RouterLink} to={`/collection/${collectionId}/file/${fsUnit.id}`} maxWidth="100%">
                    <Text noOfLines={1}>{fsUnit.name}</Text>
                </Link>
            </Flex>
        )
    }

    const renderRow = (fsUnit: FilesystemUnitMetadata) => (
        <Tr key={fsUnit.id}>
            <Td>
                <Checkbox
                    size="md"
                    isChecked={selectedFiles.some((f) => f.id === fsUnit.id)}
                    onChange={(e) =>
                        e.target.checked
                            ? setSelectedFiles([...selectedFiles, fsUnit])
                            : setSelectedFiles(selectedFiles.filter((f) => f.id !== fsUnit.id))
                    }
                    data-testid={`${SELECT_FILE_CHECKBOX}_${fsUnit.id}`}
                />
            </Td>
            <Td>{renderName(fsUnit)}</Td>
            <Td>
                {fsUnit["@type"] === "DirectoryData"
                    ? t("file-view:directory-content-list.types.directory")
                    : fsUnit.mimeType ?? t("common:unknown-masculine")}
            </Td>
            <Td>{fsUnit["@type"] === "FileData" ? <Author authorId={fsUnit.versionAuthor ?? undefined} /> : ""}</Td>
            <Td>{fsUnit["@type"] === "FileData" ? formatFileSize(i18n, fsUnit.encryptedSize) : ""}</Td>
            <Td>{fsUnit["@type"] === "FileData" ? formatDate(i18n, fsUnit.updatedAt) : ""}</Td>
            <Td>
                <ObjectActionsMenu metadata={fsUnit} fullSize={true} />
            </Td>
        </Tr>
    )

    return (
        <Table size="sm">
            <Thead>
                <Tr>
                    <Th>
                        <Checkbox
                            size="md"
                            isChecked={areAllSelected}
                            isIndeterminate={selectedFiles.length > 0 && !areAllSelected}
                            onChange={(e) => (e.target.checked ? setSelectedFiles(childObjects) : setSelectedFiles([]))}
                            data-testid={SELECT_ALL_FILES_CHECKBOX}
                        />
                    </Th>
                    <Th>{t("file-view:directory-content-list.headers.file-name")}</Th>
                    <Th>{t("file-view:directory-content-list.headers.type")}</Th>
                    <Th>{t("file-view:directory-content-list.headers.updated-by")}</Th>
                    <Th>{t("file-view:directory-content-list.headers.encrypted-size")}</Th>
                    <Th>{t("file-view:directory-content-list.headers.updated-at")}</Th>
                    <Th></Th>
                </Tr>
            </Thead>
            <Tbody>{childObjects.map(renderRow)}</Tbody>
        </Table>
    )
}

const mapStateToProps = (state: AppState) => ({
    selectedFiles: Array.from(state.files.selectedFiles),
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    setSelectedFiles: (files: FilesystemUnitMetadata[]) => dispatch(selectFilesAction(new Set(files))),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(FullDirectoryList))
