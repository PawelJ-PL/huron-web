import { Box } from "@chakra-ui/layout"
import { Portal } from "@chakra-ui/portal"
import React from "react"
import Loader from "./Loader"

type Props = {
    active: boolean
    text: string
}

const LoadingOverlay: React.FC<Props> = ({ active, text }) => {
    if (active) {
        return (
            <Portal>
                <Box
                    position="fixed"
                    left="0px"
                    top="0px"
                    height="100vh"
                    width="100vw"
                    background="blackAlpha.500"
                    zIndex="modal"
                >
                    <Box marginTop="40vh">
                        <Loader title={text} textColor="black" />
                    </Box>
                </Box>
            </Portal>
        )
    } else {
        return null
    }
}

export default LoadingOverlay
