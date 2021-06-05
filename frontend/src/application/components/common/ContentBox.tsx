import { Box, BoxProps } from "@chakra-ui/layout"
import React from "react"

type Props = {
    containerProps?: BoxProps & { "data-testid"?: string }
}

const ContentBox: React.FC<Props> = ({ children, containerProps }) => (
    <Box
        margin="0.5em"
        padding="1em"
        border="1px"
        borderColor="gray.400"
        backgroundColor="gray.50"
        borderRadius="0.3em"
        boxShadow="0.3em 0.3em 0.5em 0px rgba(0,0,0,0.3)"
        {...(containerProps || {})}
    >
        {children}
    </Box>
)

export default ContentBox
