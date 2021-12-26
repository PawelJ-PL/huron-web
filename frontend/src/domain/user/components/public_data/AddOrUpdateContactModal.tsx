import {
    Button,
    ButtonGroup,
    Checkbox,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader,
    ModalOverlay,
} from "@chakra-ui/react"
import React, { useState } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import AddOrUpdateContactForm from "./AddOrUpdateContactForm"
import capitalize from "lodash/capitalize"
import { Dispatch } from "redux"
import { createContactAction, editContactAction } from "../../store/Actions"
import { connect } from "react-redux"
import { UserContact } from "../../types/UserContact"

type ContactData = { userId: string } | UserContact

type Props = {
    isOpen: boolean
    onClose: () => void
    contactData: ContactData
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapDispatchToProps>

const isUserContact = (data: ContactData): data is UserContact => "nickName" in data

export const AddOrUpdateContactModal: React.FC<Props> = ({
    isOpen,
    onClose,
    t,
    contactData,
    createContact,
    editContact,
}) => {
    const [enableAlias, setEnableAlias] = useState<boolean>(
        isUserContact(contactData) ? Boolean(contactData.alias) : false
    )

    const handleClose = () => {
        setEnableAlias(false)
        onClose()
    }

    const onSubmit = (data: { alias?: string }) => {
        if (isUserContact(contactData)) {
            editContact(contactData.userId, data.alias)
        } else {
            createContact(contactData.userId, data.alias)
        }
        handleClose()
    }

    const defaultValues = {
        alias: isUserContact(contactData) ? contactData.alias ?? undefined : undefined,
    }

    return (
        <Modal isOpen={isOpen} onClose={handleClose}>
            <ModalOverlay />
            <ModalContent>
                <ModalHeader>
                    {isUserContact(contactData)
                        ? t("user-public-page:add-or-edit-contact-modal.edit-contact-header")
                        : t("user-public-page:add-or-edit-contact-modal.add-contact-header")}
                </ModalHeader>
                <ModalBody>
                    <Checkbox isChecked={enableAlias} onChange={() => setEnableAlias((state) => !state)}>
                        {t("user-public-page:add-or-edit-contact-modal.enable-alias")}
                    </Checkbox>
                    {enableAlias && (
                        <AddOrUpdateContactForm
                            onSubmit={onSubmit}
                            containerProps={{ marginTop: "1rem" }}
                            defaultValues={defaultValues}
                        />
                    )}
                    {!enableAlias && (
                        <form
                            id="add-contact-form"
                            onSubmit={(event) => {
                                event.preventDefault()
                                onSubmit({ alias: undefined })
                            }}
                        ></form>
                    )}
                </ModalBody>

                <ModalFooter>
                    <ButtonGroup size="xs" colorScheme="brand">
                        <Button onClick={handleClose} colorScheme="gray2">
                            {capitalize(t("common:cancel-imperative"))}
                        </Button>
                        <Button type="submit" form="add-contact-form">
                            {capitalize(t("common:confirm-imperative"))}
                        </Button>
                    </ButtonGroup>
                </ModalFooter>
            </ModalContent>
        </Modal>
    )
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    createContact: (userId: string, alias?: string) => dispatch(createContactAction.started({ userId, alias })),
    editContact: (userId: string, alias?: string) =>
        dispatch(editContactAction.started({ contactId: userId, data: { alias: { value: alias } } })),
})

export default connect(undefined, mapDispatchToProps)(withTranslation()(AddOrUpdateContactModal))
