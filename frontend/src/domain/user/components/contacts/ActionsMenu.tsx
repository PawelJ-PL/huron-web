import { IconButton, Menu, MenuButton, MenuItem, MenuList } from "@chakra-ui/react"
import React from "react"
import { withTranslation, WithTranslation } from "react-i18next"
import { BiMenu } from "react-icons/bi"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { requestContactDeleteAction, requestContactEditAction } from "../../store/Actions"
import { UserContact } from "../../types/UserContact"

type Props = {
    contact: UserContact
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapDispatchToProps>

const ActionsMenu: React.FC<Props> = ({ t, contact, requestContactDelete, requestContactEdit }) => {
    return (
        <Menu>
            <MenuButton
                as={IconButton}
                aria-label={t("contacts-list:contact-actions-label")}
                icon={<BiMenu />}
                variant="outline"
                size="sm"
            />
            <MenuList>
                <MenuItem onClick={() => requestContactEdit(contact)}>
                    {t("contacts-list:actions.edit-contact")}
                </MenuItem>
                <MenuItem onClick={() => requestContactDelete(contact)}>
                    {t("contacts-list:actions.remove-contact")}
                </MenuItem>
            </MenuList>
        </Menu>
    )
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    requestContactDelete: (contact: UserContact) => dispatch(requestContactDeleteAction(contact)),
    requestContactEdit: (contact: UserContact) => dispatch(requestContactEditAction(contact)),
})

export default connect(undefined, mapDispatchToProps)(withTranslation()(ActionsMenu))
