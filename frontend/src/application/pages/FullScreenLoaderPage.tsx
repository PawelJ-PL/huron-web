import { Image } from "@chakra-ui/image"
import { Box, Flex } from "@chakra-ui/layout"
import { Progress } from "@chakra-ui/progress"
import React from "react"
import LogoWithText from "../../resources/logo_transparent_reverse.png"
import { LOADER_PAGE } from "./testids"

const FullScreenLoaderPage: React.FC = () => (
    <Flex minHeight="100vh" justifyContent="center" alignItems="center" direction="column" data-testid={LOADER_PAGE}>
        <Box>
            <Image src={LogoWithText} alt="logo" width={["10rem"]} />
            <Progress isIndeterminate={true} marginTop="1rem" colorScheme="brand" size="xs" />
        </Box>
    </Flex>
)

export default FullScreenLoaderPage
