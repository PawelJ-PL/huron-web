import { Avatar } from "@chakra-ui/avatar"
import Icon from "@chakra-ui/icon"
import { Box, BoxProps, Flex, Link, Text, TextProps } from "@chakra-ui/layout"
import React from "react"
import { withTranslation, WithTranslation } from "react-i18next"
import { FaBan } from "react-icons/fa"
import { toSvg } from "jdenticon"
import { AppState } from "../../../../../application/store"
import { connect } from "react-redux"
import { svgContentToUrl } from "../../../../../application/utils/image"
import { Link as RouterLink } from "react-router-dom"

type Props = {
    authorId?: string
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapStateToProps>

export const Author: React.FC<Props> = ({ authorId, t, userDataResult, knownUsers }) => {
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
        const maybeUserDataResult = knownUsers[authorId]

        const renderData = (): { value: string; textProps: TextProps; avatarBoxProps: BoxProps } => {
            if (userDataResult.status === "FINISHED" && userDataResult.data.id === authorId) {
                return {
                    value: userDataResult.data.nickName,
                    textProps: { as: "b" },
                    avatarBoxProps: {},
                }
            } else if (maybeUserDataResult?.status === "FINISHED" && maybeUserDataResult.data) {
                return {
                    value: maybeUserDataResult.data.contactData?.alias ?? maybeUserDataResult.data.nickName,
                    textProps: { as: maybeUserDataResult.data.contactData?.alias ? undefined : "i" },
                    avatarBoxProps: {},
                }
            } else {
                return {
                    value: t("common:unknown-masculine"),
                    textProps: { as: "i", opacity: "0.6" },
                    avatarBoxProps: {},
                }
            }
        }

        const userData = renderData()

        return (
            <Flex alignItems="center">
                <MaybeLink userId={authorId}>
                    <Text {...userData.textProps} lineHeight="1rem" display="flex" alignItems="center" height="1.5rem">
                        {userData.value}
                    </Text>
                </MaybeLink>
                <Box display="flex" alignItems="flex-start" marginLeft="0.5ch" {...userData.avatarBoxProps}>
                    <Avatar src={svgContentToUrl(toSvg(authorId, 100))} size="2xs" />
                </Box>
            </Flex>
        )
    }
}

const mapStateToProps = (state: AppState) => ({
    userDataResult: state.users.userData,
    knownUsers: state.users.knownUsers,
})

const MaybeLink: React.FC<{ userId?: string; children?: React.ReactNode }> = ({ children, userId }) => {
    if (userId !== undefined) {
        return (
            <Link as={RouterLink} to={`/user/${userId}`}>
                {children}
            </Link>
        )
    } else {
        return <>{children}</>
    }
}

export default connect(mapStateToProps)(withTranslation()(Author))
