import { Box } from "@chakra-ui/layout"
import React from "react"
import TopBar from "./TopBar"

const DefaultLayout: React.FC = ({ children }) => (
    <Box>
        <TopBar />

        <Box paddingTop="5.3rem" marginX="2rem">
            {children}
        </Box>
    </Box>
)

export default DefaultLayout
