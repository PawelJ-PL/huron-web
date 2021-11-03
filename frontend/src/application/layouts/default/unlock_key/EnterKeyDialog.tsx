import { Button } from "@chakra-ui/button"
import { Input } from "@chakra-ui/input"
import { Modal, ModalBody, ModalContent, ModalFooter, ModalHeader, ModalOverlay } from "@chakra-ui/react"
import { capitalize } from "lodash"
import React, { useState } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { computeMasterKeyAction } from "../../../../domain/user/store/Actions"

type Props = {
    isOpen: boolean
    onClose: () => void
    emailHash: string
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapDispatchToProps>

const EnterKeyDialog: React.FC<Props> = ({ isOpen, onClose, t, unlockKey, emailHash }) => {
    const [password, setPassword] = useState("")

    const handleClose = () => {
        setPassword("")
        onClose()
    }

    return (
        <Modal isOpen={isOpen} onClose={handleClose}>
            <ModalOverlay />
            <ModalContent>
                <ModalHeader>{t("top-bar:unlock-key-button.enter-password-header")}</ModalHeader>
                <ModalBody>
                    <Input type="password" onChange={(event) => setPassword(event.target.value)} value={password} />
                </ModalBody>
                <ModalFooter>
                    <Button onClick={handleClose} marginRight="0.2em" colorScheme="gray2">
                        {capitalize(t("common:cancel-imperative"))}
                    </Button>
                    <Button
                        onClick={() => {
                            unlockKey(password, emailHash)
                            handleClose()
                        }}
                        marginLeft="0.2em"
                        colorScheme="brand"
                    >
                        {t("top-bar:unlock-key-button.enter-password-confirmation-button-label")}
                    </Button>
                </ModalFooter>
            </ModalContent>
        </Modal>
    )
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    unlockKey: (password: string, emailHash: string) =>
        dispatch(computeMasterKeyAction.started({ password, emailHash })),
})

export default connect(null, mapDispatchToProps)(withTranslation()(EnterKeyDialog))
