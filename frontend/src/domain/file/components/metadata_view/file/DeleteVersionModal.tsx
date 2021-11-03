import { Button } from "@chakra-ui/button"
import { Heading } from "@chakra-ui/layout"
import { Modal, ModalBody, ModalContent, ModalFooter, ModalOverlay } from "@chakra-ui/react"
import React from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { AppState } from "../../../../../application/store"
import { deleteVersionAction, deleteVersionRequestAction } from "../../../store/Actions"
import capitalize from "lodash/capitalize"
import { FileVersion } from "../../../types/FileVersion"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & Pick<WithTranslation, "t">

export const DeleteVersionModal: React.FC<Props> = ({ t, requestedToDelete, clearRequest, deleteVersion }) => {
    return (
        <Modal isOpen={requestedToDelete !== null} onClose={clearRequest}>
            <ModalOverlay />
            <ModalContent>
                <ModalBody>
                    <Heading as="h2" size="xs">
                        {t("file-view:file-data.delete-version-confirmation")}
                    </Heading>
                </ModalBody>

                <ModalFooter>
                    <Button size="xs" onClick={clearRequest} marginRight="0.2em" colorScheme="gray2">
                        {capitalize(t("common:cancel-imperative"))}
                    </Button>
                    <Button
                        size="xs"
                        marginLeft="0.2em"
                        colorScheme="brand"
                        onClick={() => {
                            requestedToDelete ? deleteVersion(requestedToDelete) : void 0
                            clearRequest()
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
    requestedToDelete: state.files.versionToDelete,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    clearRequest: () => dispatch(deleteVersionRequestAction(null)),
    deleteVersion: (version: FileVersion) =>
        dispatch(
            deleteVersionAction.started({
                collectionId: version.collectionId,
                fileId: version.id,
                versionId: version.versionId,
            })
        ),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(DeleteVersionModal))
