import { Box, Flex, Text } from "@chakra-ui/layout"
import React, { useState } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import ContentBox from "../../../../application/components/common/ContentBox"
import { UserData } from "../../types/UserData"
import { FaUserCog } from "react-icons/fa"
import { FaLock } from "react-icons/fa"
import { IoKeySharp } from "react-icons/io5"
import { useBreakpointValue } from "@chakra-ui/media-query"
import UpdateProfileForm from "./UpdateProfileForm"
import APiKeysList from "./ApiKeysView"
import ChangePasswordForm from "./ChangePasswordForm"

type Props = {
    userData: UserData
} & Pick<WithTranslation, "t">

type Section = "userProfile" | "changePassword" | "apiKeys"

export const UserProfileView: React.FC<Props> = ({ userData, t }) => {
    const [selectedSection, setSelectedSection] = useState<Section>("userProfile")
    const menuOnTop = useBreakpointValue({ base: true, md: false })
    const menu = {
        width: menuOnTop ? "100%" : "25%",
        marginRight: menuOnTop ? "0" : "0.5rem",
        marginBottom: menuOnTop ? "0.5rem" : "0",
    }

    const content = {
        width: menuOnTop ? "100%" : "75%",
        marginLeft: menuOnTop ? "0" : "0.5rem",
        marginTop: menuOnTop ? "0.5rem" : "0",
    }

    const renderContent = () => {
        switch (selectedSection) {
            case "userProfile":
                return <UpdateProfileForm currentData={userData} />
            case "changePassword":
                return <ChangePasswordForm />
            case "apiKeys":
                return <APiKeysList />
            default:
                return null
        }
    }

    return (
        <Flex direction={menuOnTop ? "column" : "row"}>
            <Box
                maxWidth={menu.width}
                flexBasis={menu.width}
                marginRight={menu.marginRight}
                marginBottom={menu.marginBottom}
            >
                <MenuEntry
                    isSelected={selectedSection === "userProfile"}
                    onClick={() => setSelectedSection("userProfile")}
                >
                    <Flex alignItems="center">
                        <FaUserCog /> <Text marginLeft="0.5em">{t("profile-page:sections.user-profile")}</Text>
                    </Flex>
                </MenuEntry>
                <MenuEntry
                    isSelected={selectedSection === "changePassword"}
                    onClick={() => setSelectedSection("changePassword")}
                >
                    <Flex alignItems="center">
                        <FaLock /> <Text marginLeft="0.5em">{t("profile-page:sections.change-password")}</Text>
                    </Flex>
                </MenuEntry>
                <MenuEntry isSelected={selectedSection === "apiKeys"} onClick={() => setSelectedSection("apiKeys")}>
                    <Flex alignItems="center">
                        <IoKeySharp /> <Text marginLeft="0.5em">{t("profile-page:sections.api-keys")}</Text>
                    </Flex>
                </MenuEntry>
            </Box>
            <Box
                maxWidth={content.width}
                flexBasis={content.width}
                marginLeft={content.marginLeft}
                marginTop={content.marginTop}
            >
                <ContentBox containerProps={{ margin: "0" }}>{renderContent()}</ContentBox>
            </Box>
        </Flex>
    )
}

type MenuEntryProps = { isSelected?: boolean; onClick: () => void }

const MenuEntry: React.FC<MenuEntryProps> = ({ children, isSelected, onClick }) => (
    <Box
        background={isSelected ? "brand.500" : "inherit"}
        paddingY="0.75em"
        paddingX="1.5em"
        rounded="md"
        cursor="pointer"
        onClick={onClick}
    >
        <Box color={isSelected ? "white" : "text"}>{children}</Box>
    </Box>
)

export default withTranslation()(UserProfileView)
