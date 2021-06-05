import { Button } from "@chakra-ui/button"
import { Modal, ModalBody, ModalContent, ModalFooter, ModalHeader, ModalOverlay } from "@chakra-ui/modal"
import React, { useCallback, useState } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import capitalize from "lodash/capitalize"
import ApiKeyExpirationPicker from "./ApiKeyExpirationPicker"

type Props = {
    isOpen: boolean
    onClose: () => void
    onConfirm: (result: string | null) => void
    defaultValidTo?: string | null
} & Pick<WithTranslation, "t" | "i18n">

const ApiKeyExpirationDateModal: React.FC<Props> = ({ isOpen, onClose, onConfirm, defaultValidTo, t, i18n }) => {
    const [selectedDate, setSelectedDate] = useState<Date | null>(defaultValidTo ? new Date(defaultValidTo) : null)
    const onDateChange = useCallback((d: Date | null) => setSelectedDate(d), [setSelectedDate])

    return (
        <Modal isOpen={isOpen} onClose={onClose}>
            <ModalOverlay />
            <ModalContent>
                <ModalHeader>{t("profile-page:api-key-expirataion-modal.header")}</ModalHeader>
                <ModalBody>
                    <ApiKeyExpirationPicker defaultValidTo={defaultValidTo ?? undefined} onChange={onDateChange} />
                </ModalBody>

                <ModalFooter>
                    <Button size="xs" onClick={onClose} marginRight="0.2em" colorScheme="gray2">
                        {capitalize(t("common:cancel-imperative"))}
                    </Button>
                    <Button
                        size="xs"
                        onClick={() => {
                            onConfirm(selectedDate ? selectedDate.toISOString() : null)
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
}

export default withTranslation()(ApiKeyExpirationDateModal)
