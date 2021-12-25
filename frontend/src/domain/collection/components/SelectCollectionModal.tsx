import { Button } from "@chakra-ui/button"
import { Modal, ModalBody, ModalContent, ModalFooter, ModalHeader, ModalOverlay } from "@chakra-ui/react"
import { Select } from "@chakra-ui/select"
import React, { useState } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { Collection } from "../types/Collection"
import capitalize from "lodash/capitalize"
import { useNavigate } from "react-router-dom"

type Props = {
    availableCollections: Collection[]
    selectedCollection?: string
    onClose?: () => void
} & Pick<WithTranslation, "t">

const SelectCollectionModal: React.FC<Props> = ({ availableCollections, t, onClose, selectedCollection }) => {
    const [selected, setSelected] = useState(selectedCollection ?? availableCollections[0].id)

    const navigate = useNavigate()

    return (
        <Modal isOpen={true} onClose={onClose ?? (() => void 0)}>
            <ModalOverlay />
            <ModalContent>
                <ModalHeader>{t("collections-view:select-collection-modal:header")}</ModalHeader>
                <ModalBody>
                    <Select
                        onChange={(e) => setSelected(e.target.value)}
                        defaultValue={selectedCollection ?? availableCollections[0].id}
                    >
                        {availableCollections.map((collection) => (
                            <option key={collection.id} value={collection.id}>
                                {collection.name}
                            </option>
                        ))}
                    </Select>
                </ModalBody>
                <ModalFooter>
                    <Button
                        colorScheme="brand"
                        size="sm"
                        onClick={() => {
                            if (onClose) {
                                onClose()
                            }
                            navigate(`/collection/${selected}`)
                        }}
                    >
                        {capitalize(t("common:confirm-imperative"))}
                    </Button>
                </ModalFooter>
            </ModalContent>
        </Modal>
    )
}

export default withTranslation()(SelectCollectionModal)
