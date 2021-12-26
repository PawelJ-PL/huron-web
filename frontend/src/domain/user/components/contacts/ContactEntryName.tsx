import { Avatar, Box, Flex, Heading } from "@chakra-ui/react"
import { toSvg } from "jdenticon"
import React from "react"
import { Link } from "react-router-dom"
import { svgContentToUrl } from "../../../../application/utils/image"
import { UserContact } from "../../types/UserContact"

type Props = {
    contact: UserContact
}

const ContactEntryName: React.FC<Props> = ({ contact }) => (
    <Flex alignItems="center">
        <Box marginRight="1.3rem">
            <Avatar src={svgContentToUrl(toSvg(contact.userId, 20))} size="sm" />
        </Box>
        <Link to={`/user/${contact.userId}`}>
            <Box>
                <Heading as="h5" size="sm" wordBreak="break-word">
                    {contact.alias}
                </Heading>
                <Heading as="h6" size="xs" opacity="0.6" wordBreak="break-word">
                    {contact.nickName}
                </Heading>
            </Box>
        </Link>
    </Flex>
)

export default ContactEntryName
