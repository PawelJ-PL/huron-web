import { Image } from "@chakra-ui/image"
import { Flex } from "@chakra-ui/layout"
import React from "react"
import ContentBox from "../../../application/components/common/ContentBox"
import LogoWithText from "../../../resources/logo_transparent_reverse.png"

type Props = {
    children?: React.ReactNode
    outsideElement?: JSX.Element
}

const UserFormBox: React.FC<Props> = ({ children, outsideElement }) => (
    <Flex minHeight="100vh" alignItems="center" paddingTop="3vh" direction="column">
        <ContentBox
            containerProps={{
                display: "flex",
                flexDir: "column",
                alignItems: "center",
                maxWidth: ["90%", "70%", null, "40rem"],
            }}
        >
            <Image src={LogoWithText} alt="logo" width="20rem" />
            {children}
        </ContentBox>
        {outsideElement !== undefined && outsideElement}
    </Flex>
)

export default UserFormBox
