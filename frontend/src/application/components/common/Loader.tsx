import { Box, Flex, Heading } from "@chakra-ui/layout"
import { Progress } from "@chakra-ui/progress"
import React from "react"

type Props = {
    title: string
    textColor?: string
}

const Loader: React.FC<Props> = ({ title, textColor }) => (
    <Flex justifyContent="center" alignItems="center" direction="column" textAlign="center">
        <Box maxWidth="15ch">
            <Progress isIndeterminate={true} marginTop="1rem" colorScheme="brand" size="xs" />
            <Heading as="h6" size="xs" color={textColor}>
                {title}
            </Heading>
        </Box>
    </Flex>
)

export default Loader
