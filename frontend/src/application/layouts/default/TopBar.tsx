import { Avatar } from "@chakra-ui/avatar"
import { Image } from "@chakra-ui/image"
import { Box, Text } from "@chakra-ui/layout"
import { useBreakpointValue } from "@chakra-ui/media-query"
import { Menu, MenuButton, MenuDivider, MenuGroup, MenuItem, MenuList } from "@chakra-ui/menu"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Link as RouterLink, RouteComponentProps, withRouter } from "react-router-dom"
import Logo from "../../../resources/logo_transparent.png"
import { AppState } from "../../store"
import LanguagePicker from "./LanguagePicker"
import { FiLogOut } from "react-icons/fi"
import { Dispatch } from "redux"
import { apiLogoutAction, resetApiLogoutStatusAction } from "../../../domain/user/store/Actions"
import { useToast } from "@chakra-ui/toast"

type Props = ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps> &
    Pick<WithTranslation, "t"> &
    Pick<RouteComponentProps, "history">

export const TopBar: React.FC<Props> = ({ userData, t, history, logout, clearLogoutStatus, logoutStatus }) => {
    const showUserName = useBreakpointValue({ base: false, sm: true })

    const toast = useToast({ position: "top", isClosable: true })

    useEffect(() => {
        clearLogoutStatus()
        return () => {
            clearLogoutStatus()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        if (logoutStatus === "FAILED") {
            toast({ status: "error", title: t("top-bar:logout-failed-message") })
            clearLogoutStatus()
        }
    }, [clearLogoutStatus, logoutStatus, t, toast])

    return (
        <Box
            height="5rem"
            background="brand.500"
            boxSizing="border-box"
            position="fixed"
            left="0"
            right="0"
            top="0"
            textColor="white"
            zIndex="sticky"
        >
            <Box marginX="2rem" display="flex" alignItems="center">
                <Box height="5rem" display="flex" alignItems="center">
                    <RouterLink to="/">
                        <Image src={Logo} height="4rem" verticalAlign="middle" />
                    </RouterLink>
                </Box>
                <Box marginLeft="auto" display="flex" alignItems="center">
                    <LanguagePicker />
                    <Menu autoSelect={false}>
                        <MenuButton>
                            <Box display="flex" alignItems="center" marginLeft="1.5em">
                                <Avatar background="brand.200" size="sm" />
                                {showUserName && (
                                    <Text fontSize="sm" marginLeft="0.3em">
                                        {userData?.nickName ?? t("top-bar:unknown-user")}
                                    </Text>
                                )}
                            </Box>
                        </MenuButton>
                        <MenuList background="brand.400">
                            <MenuGroup>
                                <MenuItem _hover={{ color: "black" }} onClick={() => history.push("/profile")}>
                                    {t("top-bar:account-menu-items.profile")}
                                </MenuItem>
                            </MenuGroup>
                            <MenuDivider />
                            <MenuGroup>
                                <MenuItem _hover={{ color: "black" }} icon={<FiLogOut />} onClick={logout}>
                                    {t("top-bar:account-menu-items.logout")}
                                </MenuItem>
                            </MenuGroup>
                        </MenuList>
                    </Menu>
                </Box>
            </Box>
        </Box>
    )
}

const mapStateToProps = (state: AppState) => ({
    userData: state.users.userData.status === "FINISHED" ? state.users.userData.data : undefined,
    logoutStatus: state.users.logoutStatus.status,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    logout: () => dispatch(apiLogoutAction.started()),
    clearLogoutStatus: () => dispatch(resetApiLogoutStatusAction()),
})

export default withRouter(connect(mapStateToProps, mapDispatchToProps)(withTranslation()(TopBar)))
