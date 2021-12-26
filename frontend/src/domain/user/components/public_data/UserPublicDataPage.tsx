import { Avatar, Box, Center, Heading } from "@chakra-ui/react"
import { toSvg } from "jdenticon"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import AlertBox from "../../../../application/components/common/AlertBox"
import LoadingOverlay from "../../../../application/components/common/LoadingOverlay"
import UnexpectedErrorMessage from "../../../../application/components/common/UnexpectedErrorMessage"
import { AppState } from "../../../../application/store"
import { svgContentToUrl } from "../../../../application/utils/image"
import { ContactWithAliasAlreadyExists } from "../../api/errors"
import {
    resetCreateContactResultAction,
    resetDeleteContactResultAction,
    resetEditContactResultAction,
} from "../../store/Actions"
import { UserPublicData } from "../../types/UserPublicData"
import PublicUserDataActionsPanel from "./PublicUserDataActionsPanel"

type Props = {
    userPublicData: UserPublicData
    self: boolean
} & ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps> &
    Pick<WithTranslation, "t">

export const UserPublicDataPage: React.FC<Props> = ({
    userPublicData,
    self,
    actionInProgress,
    removeContactResult,
    resetRemoveContactResult,
    t,
    createContactResult,
    resetCreateContactResult,
    editContactResult,
    resetEditContactResult,
}) => {
    const resetStatuses = () => {
        resetRemoveContactResult()
        resetCreateContactResult()
        resetEditContactResult()
    }

    useEffect(() => {
        resetStatuses()
        return () => {
            resetStatuses()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const renderError = (error: Error, onClose?: () => void) => {
        if (error instanceof ContactWithAliasAlreadyExists) {
            return (
                <AlertBox
                    title={t("user-public-page:alias-already-exists-message", { alias: error.alias })}
                    status="warning"
                    onClose={onClose}
                />
            )
        } else {
            return <UnexpectedErrorMessage error={error} onClose={onClose} />
        }
    }

    return (
        <Box marginX="1.5rem">
            <LoadingOverlay active={actionInProgress} text={t("common:action-in-progress")} />
            {removeContactResult.status === "FAILED" &&
                renderError(removeContactResult.error, resetRemoveContactResult)}
            {createContactResult.status === "FAILED" &&
                renderError(createContactResult.error, resetCreateContactResult)}
            {editContactResult.status === "FAILED" && renderError(editContactResult.error, resetEditContactResult)}
            <Center flexDirection="column">
                <Avatar src={svgContentToUrl(toSvg(userPublicData.userId, 20))} size="xl" />
                <Heading as="h3" size="lg" marginTop="0.3rem">
                    {userPublicData.contactData?.alias ?? userPublicData.nickName}
                </Heading>
                {userPublicData.contactData?.alias && (
                    <Heading as="h5" size="sm" opacity="0.5">
                        {userPublicData.nickName}
                    </Heading>
                )}
                <PublicUserDataActionsPanel userPublicData={userPublicData} self={self} />
            </Center>
        </Box>
    )
}

const mapStateToProps = (state: AppState) => ({
    actionInProgress: [
        state.users.deleteContactResult.status,
        state.users.createContactResult.status,
        state.users.editContactResult.status,
    ].includes("PENDING"),
    removeContactResult: state.users.deleteContactResult,
    createContactResult: state.users.createContactResult,
    editContactResult: state.users.editContactResult,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    resetRemoveContactResult: () => dispatch(resetDeleteContactResultAction()),
    resetCreateContactResult: () => dispatch(resetCreateContactResultAction()),
    resetEditContactResult: () => dispatch(resetEditContactResultAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(UserPublicDataPage))
