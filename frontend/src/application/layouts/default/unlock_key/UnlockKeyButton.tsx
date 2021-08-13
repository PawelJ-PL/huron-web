import { Box, BoxProps } from "@chakra-ui/layout"
import React from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { AppState } from "../../../store"
import { FaLockOpen } from "react-icons/fa"
import { FaLock } from "react-icons/fa"
import { FaExclamation } from "react-icons/fa"
import { IconButton } from "@chakra-ui/button"
import { Tooltip } from "@chakra-ui/tooltip"
import EnterKeyDialog from "./EnterKeyDialog"
import { useDisclosure } from "@chakra-ui/hooks"
import { Dispatch } from "redux"
import { clearMasterKeyAction } from "../../../../domain/user/store/Actions"
import { LOCK_KEY_BUTTON, LOCK_KEY_TOOLTIP } from "../testids"

type Props = {
    containerProps?: BoxProps
    userEmailHash: string
} & ReturnType<typeof mapStateToProps> &
    Pick<WithTranslation, "t"> &
    ReturnType<typeof mapDispatchToProps>

type Status = "Failed" | "Pending" | "Locked" | "Unlocked"

export const UnlockKeyButton: React.FC<Props> = ({ containerProps, status, t, userEmailHash, lockKey }) => {
    const keyDialogDisclosure = useDisclosure()

    const componentProps = () => {
        switch (status) {
            case "Locked":
                return {
                    "aria-label": t("top-bar:unlock-key-button.key-locked-label"),
                    icon: <FaLock />,
                    isLoading: false,
                    isDisabled: false,
                    toolTipText: t("top-bar:unlock-key-button.key-locked-tooltip"),
                    onClick: keyDialogDisclosure.onOpen,
                }
            case "Unlocked":
                return {
                    "aria-label": t("top-bar:unlock-key-button.key-unlocked-label"),
                    icon: <FaLockOpen />,
                    isLoading: false,
                    isDisabled: false,
                    toolTipText: t("top-bar:unlock-key-button.key-unlocked-tooltip"),
                    onClick: lockKey,
                }
            case "Pending":
                return {
                    "aria-label": t("top-bar:unlock-key-button.key-pending-label"),
                    icon: <FaLock />,
                    isLoading: true,
                    isDisabled: true,
                    toolTipText: t("top-bar:unlock-key-button.key-pending-tooltip"),
                }
            case "Failed":
                return {
                    "aria-label": t("top-bar:unlock-key-button.key-error-label"),
                    icon: <FaExclamation />,
                    isLoading: false,
                    isDisabled: false,
                    toolTipText: t("top-bar:unlock-key-button.key-error-tooltip"),
                    onClick: keyDialogDisclosure.onOpen,
                }
        }
    }

    const { toolTipText, ...iconProps } = componentProps()

    return (
        <Box {...(containerProps ?? {})}>
            <EnterKeyDialog
                isOpen={keyDialogDisclosure.isOpen}
                onClose={keyDialogDisclosure.onClose}
                emailHash={userEmailHash}
            />
            <Tooltip label={toolTipText} data-testid={LOCK_KEY_TOOLTIP}>
                <IconButton
                    {...iconProps}
                    variant="outline"
                    _hover={{ background: "inherit" }}
                    size="sm"
                    data-testid={LOCK_KEY_BUTTON}
                />
            </Tooltip>
        </Box>
    )
}

const mapStateToProps = (state: AppState) => ({
    status: getStatus(state),
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    lockKey: () => dispatch(clearMasterKeyAction()),
})

function getStatus(state: AppState): Status {
    const masterKeyStatus = state.users.masterKey.status
    const keyPairStatus = state.users.keyPair.status
    const collectionKeyResult = state.collections.encryptionKey
    const collectionKeyStatus = collectionKeyResult.status

    if (masterKeyStatus === "FAILED" || keyPairStatus === "FAILED" || collectionKeyStatus === "FAILED") {
        return "Failed"
    } else if (masterKeyStatus === "PENDING" || keyPairStatus === "PENDING" || collectionKeyStatus === "PENDING") {
        return "Pending"
    } else if (masterKeyStatus === "FINISHED" && keyPairStatus === "FINISHED" && collectionKeyUnlocked(state)) {
        return "Unlocked"
    } else {
        return "Locked"
    }
}

function collectionKeyUnlocked(state: AppState): boolean {
    const currentCollection = state.collections.activeCollection
    if (!currentCollection) {
        return true
    }
    const collectionKey = state.collections.encryptionKey
    return collectionKey.status === "FINISHED" && collectionKey.params.collectionId === currentCollection
}

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(UnlockKeyButton))
