import Icon from "@chakra-ui/icon"
import { Flex, Heading } from "@chakra-ui/layout"
import React from "react"
import { IconType } from "react-icons/lib"

type Props = {
    children?: React.ReactNode
    text: string
    icon: IconType
}

const EmptyPlaceholder: React.FC<Props> = ({ text, icon, children }) => (
    <Flex direction="column" alignItems="center" justifyContent="center">
        <Icon as={icon} boxSize="4.5rem" />
        <Heading as="h3" size="lg" textAlign="center">
            {text}
        </Heading>
        {children}
    </Flex>
)

export default EmptyPlaceholder
