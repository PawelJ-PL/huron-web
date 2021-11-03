import { Modal, ModalBody, ModalContent, ModalHeader, ModalOverlay } from "@chakra-ui/react"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import AlertBox from "../../../application/components/common/AlertBox"
import Loader from "../../../application/components/common/Loader"
import { AppState } from "../../../application/store"
import { listCollectionsAction, resetAvailableCollectionsListAction } from "../store/Actions"
import SelectCollectionModal from "./SelectCollectionModal"

type Props = {
    isOpen: boolean
    onClose: () => void
} & ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps> &
    Pick<WithTranslation, "t">

export const FetchCollectionsModalWrapper: React.FC<Props> = ({
    isOpen,
    onClose,
    collectionsResult,
    fetchCollections,
    activeCollection,
    t,
    resetCollections,
}) => {
    useEffect(() => {
        if (isOpen && ["NOT_STARTED", "FAILED"].includes(collectionsResult.status)) {
            fetchCollections()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOpen])

    const handleClose = () => {
        resetCollections()
        onClose()
    }

    const modal = (body: JSX.Element) => (
        <Modal isOpen={isOpen} onClose={handleClose}>
            <ModalOverlay />
            <ModalContent>
                <ModalHeader>{t("collections-view:select-collection-modal:header")}</ModalHeader>
                <ModalBody>{body}</ModalBody>
            </ModalContent>
        </Modal>
    )

    if (isOpen && collectionsResult.status === "FINISHED") {
        return (
            <SelectCollectionModal
                availableCollections={collectionsResult.data}
                onClose={handleClose}
                selectedCollection={activeCollection ?? undefined}
            />
        )
    } else if (isOpen && collectionsResult.status === "PENDING") {
        return modal(<Loader title={t("collections-view:loader-title")} />)
    } else if (isOpen && collectionsResult.status === "FAILED") {
        return modal(<AlertBox icon={true} status="error" title={t("collections-view:load-error-title")} />)
    } else {
        return null
    }
}

const mapStateToProps = (state: AppState) => ({
    collectionsResult: state.collections.availableCollections,
    activeCollection: state.collections.activeCollection,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchCollections: () => dispatch(listCollectionsAction.started(true)),
    resetCollections: () => dispatch(resetAvailableCollectionsListAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(FetchCollectionsModalWrapper))
