import { Avatar } from "@chakra-ui/avatar"
import Icon from "@chakra-ui/icon"
import { Box, BoxProps, Flex, Text, TextProps } from "@chakra-ui/layout"
import React from "react"
import { withTranslation, WithTranslation } from "react-i18next"
import { FaBan } from "react-icons/fa"
import { toSvg } from "jdenticon"
import { AppState } from "../../../../../application/store"
import { connect } from "react-redux"

const svgContentToUrl = (svgContent: string) => {
    const base64Data = btoa(svgContent)
    return "data:image/svg+xml;base64," + base64Data
}

type Props = {
    authorId?: string
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapStateToProps>

const Author: React.FC<Props> = ({ authorId, t, userDataResult }) => {
    if (!authorId) {
        return (
            <Flex alignContent="center" alignItems="center">
                <Text as="i" opacity="0.6" display="flex">
                    {t("file-view:file-data.account-deleted")}
                </Text>
                <Icon as={FaBan} marginLeft="0.5ch" opacity="0.6" />
            </Flex>
        )
    } else {
        const renderData: { value: string; textProps: TextProps; avatarBoxProps: BoxProps } =
            userDataResult.status === "FINISHED" && userDataResult.data.id === authorId
                ? {
                      value: userDataResult.data.nickName,
                      textProps: { as: "b" },
                      avatarBoxProps: {},
                  }
                : {
                      value: t("common:unknown-masculine"),
                      textProps: { as: "i", opacity: "0.6" },
                      avatarBoxProps: {},
                  }

        return (
            <Flex alignItems="center">
                <Text {...renderData.textProps} lineHeight="1rem" display="flex" alignItems="center" height="1.5rem">
                    {renderData.value}
                </Text>
                <Box display="flex" alignItems="flex-start" marginLeft="0.5ch" {...renderData.avatarBoxProps}>
                    <Avatar src={svgContentToUrl(toSvg(authorId, 100))} size="2xs" />
                </Box>
            </Flex>
        )
    }
}

const mapStateToProps = (state: AppState) => ({
    userDataResult: state.users.userData,
})

export default connect(mapStateToProps)(withTranslation()(Author))
