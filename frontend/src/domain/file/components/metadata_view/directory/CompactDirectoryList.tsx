import { Checkbox } from "@chakra-ui/react"
import { Box, Divider, Flex, Heading, Text } from "@chakra-ui/layout"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Link } from "react-router-dom"
import { Dispatch } from "redux"
import { AppState } from "../../../../../application/store"
import { selectFilesAction } from "../../../store/Actions"
import { FilesystemUnitMetadata } from "../../../types/FilesystemUnitMetadata"
import { formatDate, formatFileSize } from "../../fieldFormatters"
import { SELECT_FILE_CHECKBOX } from "../../testids"
import { iconForFileType } from "./DirectoryView"
import ObjectActionsMenu from "./ObjectActionsMenu"

type Props = {
    childObjects: FilesystemUnitMetadata[]
    collectionId: string
} & Pick<WithTranslation, "i18n"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

export const CompactDirectoryList: React.FC<Props> = ({
    childObjects,
    collectionId,
    i18n,
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

    const renderItem = (unit: FilesystemUnitMetadata) => {
        const FileIcon =
            unit["@type"] === "DirectoryData"
                ? iconForFileType("directory")
                : iconForFileType(unit.mimeType ?? "unknown")

        return (
            <Box key={unit.id} marginTop="0.5rem">
                <Flex alignItems="center" justifyContent="center">
                    <Box>
                        <Checkbox
                            isChecked={selectedFiles.some((f) => f.id === unit.id)}
                            onChange={(e) =>
                                e.target.checked
                                    ? setSelectedFiles([...selectedFiles, unit])
                                    : setSelectedFiles(selectedFiles.filter((f) => f.id !== unit.id))
                            }
                            data-testid={`${SELECT_FILE_CHECKBOX}_${unit.id}`}
                        />
                    </Box>
                    <Box marginX="0.5rem">
                        <FileIcon size={28} />
                    </Box>
                    <Flex direction="column" width={["55%", "70%", "80%"]}>
                        <Link to={`/collection/${collectionId}/file/${unit.id}`}>
                            <Heading isTruncated={true} as="h4" size="md">
                                {unit.name}
                            </Heading>
                        </Link>
                        <Flex>
                            <Text fontSize="xs" opacity="0.7">
                                {unit["@type"] === "FileData" ? formatFileSize(i18n, unit.encryptedSize) : ""}
                            </Text>
                            <Text fontSize="xs" opacity="0.7" marginRight="0" marginLeft="auto">
                                {unit["@type"] === "FileData" ? formatDate(i18n, unit.updatedAt) : ""}
                            </Text>
                        </Flex>
                    </Flex>
                    <Box paddingBottom="0.2rem" marginLeft="0.2rem">
                        <ObjectActionsMenu metadata={unit} fullSize={false} />
                    </Box>
                </Flex>
                <Divider />
            </Box>
        )
    }

    return <Box marginTop="0.5rem">{childObjects.map(renderItem)}</Box>
}

const mapStateToProps = (state: AppState) => ({
    selectedFiles: Array.from(state.files.selectedFiles),
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    setSelectedFiles: (files: FilesystemUnitMetadata[]) => dispatch(selectFilesAction(new Set(files))),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(CompactDirectoryList))
