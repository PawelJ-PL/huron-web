import { Box } from "@chakra-ui/layout"
import React from "react"
import TopBar from "./TopBar"

type Props = {
    children?: React.ReactNode
}

const DefaultLayout: React.FC<Props> = ({ children }) => (
    <Box>
        <TopBar />

        <Box paddingTop="5.3rem" marginX="2rem">
            {children}
        </Box>
    </Box>
)

export default DefaultLayout
