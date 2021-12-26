import { Table, Tbody, Td, Tr } from "@chakra-ui/react"
import React from "react"
import { UserContact } from "../../types/UserContact"
import ActionsMenu from "./ActionsMenu"
import ContactEntryName from "./ContactEntryName"

type Props = {
    contacts: UserContact[]
}

const ContactsListTable: React.FC<Props> = ({ contacts }) => {
    const renderRecord = (contact: UserContact) => (
        <Tr key={contact.userId}>
            <Td>
                <ContactEntryName contact={contact} />
            </Td>
            <Td textAlign="end">
                <ActionsMenu contact={contact} />
            </Td>
        </Tr>
    )

    return (
        <Table variant="simple" size="sm">
            <Tbody>{contacts.map((contact) => renderRecord(contact))}</Tbody>
        </Table>
    )
}

export default ContactsListTable
