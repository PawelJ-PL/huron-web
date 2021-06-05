import { Image } from "@chakra-ui/image"
import { Flex, Heading } from "@chakra-ui/layout"
import React from "react"
import Logo from "../../resources/logo_transparent_reverse.png"

type Props = {
    title: string
    description: string
}

const ErrorPage: React.FC<Props> = ({ title, description }) => (
    <Flex
        direction="column"
        padding="1.5em"
        rounded="md"
        justifyContent="center"
        alignItems="center"
        textAlign="center"
        minHeight="30vh"
        minWidth="30vw"
    >
        <Image src={Logo} alt="logo" width={["10rem", null, null, null, null, "20rem"]} />
        <Heading as="h2" fontSize={["xl", null, null, null, null, "4xl"]} marginTop="2rem" color="brand.500">
            {title}
        </Heading>
        <Heading as="h6" fontSize={["xs", null, null, null, null, "lg"]} marginTop="1rem" color="brand.300">
            {description}
        </Heading>
    </Flex>
)

export default ErrorPage
