import { Button } from "@chakra-ui/button"
import { Checkbox } from "@chakra-ui/checkbox"
import { Heading } from "@chakra-ui/layout"
import { Modal, ModalBody, ModalContent, ModalFooter, ModalOverlay } from "@chakra-ui/modal"
import React, { useState } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { AppState } from "../../../../../application/store"
import { deleteFilesAction, deleteFilesRequestAction } from "../../../store/Actions"
import capitalize from "lodash/capitalize"
import { DELETE_FILE_CONFIRMATION_MODAL } from "../../testids"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & Pick<WithTranslation, "t">

export const DeleteFilesConfirmation: React.FC<Props> = ({ requestedFiles, clearRequest, t, deleteFiles }) => {
    const [recursively, setRecursively] = useState(false)

    const handleExit = () => {
        setRecursively(false)
        clearRequest()
    }

    return (
        <Modal
            isOpen={requestedFiles !== null && new Set(requestedFiles.map((f) => f.collectionId)).size === 1}
            onClose={handleExit}
        >
            <ModalOverlay />
            <ModalContent data-testid={DELETE_FILE_CONFIRMATION_MODAL}>
                <ModalBody>
                    <Heading as="h2" size="xs">
                        {t("file-view:directory-content-list.delete-files-confirmation")}{" "}
                        {t("file-view:directory-content-list.file-or-directory-count", {
                            count: requestedFiles?.length ?? 0,
                        })}
                        ?
                    </Heading>
                    {requestedFiles?.some((f) => f["@type"] === "DirectoryData") && (
                        <Checkbox size="sm" isChecked={recursively} onChange={(e) => setRecursively(e.target.checked)}>
                            {t("file-view:directory-content-list.delete-recursively")}
                        </Checkbox>
                    )}
                </ModalBody>

                <ModalFooter>
                    <Button size="xs" onClick={handleExit} marginRight="0.2em" colorScheme="gray2">
                        {capitalize(t("common:cancel-imperative"))}
                    </Button>
                    <Button
                        size="xs"
                        marginLeft="0.2em"
                        colorScheme="brand"
                        onClick={() => {
                            deleteFiles(
                                requestedFiles ? requestedFiles[0].collectionId : "",
                                requestedFiles?.map((f) => f.id) ?? [],
                                recursively
                            )
                            handleExit()
                        }}
                    >
                        {capitalize(t("common:confirm-imperative"))}
                    </Button>
                </ModalFooter>
            </ModalContent>
        </Modal>
    )
}

const mapStateToProps = (state: AppState) => ({
    requestedFiles: state.files.deleteFilesRequest,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    clearRequest: () => dispatch(deleteFilesRequestAction(null)),
    deleteFiles: (collectionId: string, fileIds: string[], deleteNonEmpty: boolean) =>
        dispatch(deleteFilesAction.started({ collectionId, fileIds, deleteNonEmpty })),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(DeleteFilesConfirmation))
