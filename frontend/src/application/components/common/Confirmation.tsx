import { Button } from "@chakra-ui/button"
import { Modal, ModalBody, ModalContent, ModalFooter, ModalHeader, ModalOverlay } from "@chakra-ui/modal"
import React from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import capitalize from "lodash/capitalize"

type Props = {
    isOpen: boolean
    onClose: () => void
    onConfirm: () => void
    content: string
    title?: string
} & Pick<WithTranslation, "t">

const Confirmation: React.FC<Props> = ({ isOpen, onClose, onConfirm, content, title, t }) => (
    <Modal isOpen={isOpen} onClose={onClose} size="sm">
        <ModalOverlay />
        <ModalContent>
            {title && <ModalHeader>{title}</ModalHeader>}
            <ModalBody>{content}</ModalBody>
            <ModalFooter>
                <Button size="xs" onClick={onClose} marginRight="0.2em" colorScheme="gray2">
                    {capitalize(t("common:cancel-imperative"))}
                </Button>
                <Button
                    size="xs"
                    onClick={() => {
                        onConfirm()
                        onClose()
                    }}
                    marginLeft="0.2em"
                    colorScheme="brand"
                >
                    {capitalize(t("common:confirm-imperative"))}
                </Button>
            </ModalFooter>
        </ModalContent>
    </Modal>
)

export default withTranslation()(Confirmation)
