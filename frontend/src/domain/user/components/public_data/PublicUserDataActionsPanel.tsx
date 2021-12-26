import { Box, Button, useDisclosure, Wrap } from "@chakra-ui/react"
import React, { useState } from "react"
import { withTranslation, WithTranslation } from "react-i18next"
import { connect } from "react-redux"
import { useNavigate } from "react-router-dom"
import { Dispatch } from "redux"
import Confirmation from "../../../../application/components/common/Confirmation"
import { deleteContactAction } from "../../store/Actions"
import { UserContact } from "../../types/UserContact"
import { UserPublicData } from "../../types/UserPublicData"
import AddOrUpdateContactModal from "./AddOrUpdateContactModal"

type Props = {
    userPublicData: UserPublicData
    self: boolean
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapDispatchToProps>

export const PublicUserDataActionsPanel: React.FC<Props> = ({ self, userPublicData, t, removeFromContacts }) => {
    const navigate = useNavigate()
    const removeContactConfirmation = useDisclosure()
    const [editModalData, setEditModalData] = useState<{ userId: string } | UserContact | null>(null)

    return (
        <Box>
            <Confirmation
                isOpen={removeContactConfirmation.isOpen}
                onClose={removeContactConfirmation.onClose}
                onConfirm={() => removeFromContacts(userPublicData.userId)}
                content={t("user-public-page:remove-contact-confirmation.content", {
                    name: userPublicData.contactData?.alias ?? userPublicData.nickName,
                })}
                title={t("user-public-page:remove-contact-confirmation.header")}
            />
            {editModalData && (
                <AddOrUpdateContactModal
                    isOpen={true}
                    onClose={() => setEditModalData(null)}
                    contactData={editModalData}
                />
            )}
            <Wrap marginTop="1rem" direction={["column", "row"]}>
                {!self && !userPublicData.contactData && (
                    <Button
                        colorScheme="brand"
                        size="sm"
                        onClick={() => setEditModalData({ userId: userPublicData.userId })}
                    >
                        {t("user-public-page:save-contact-button")}
                    </Button>
                )}
                {!self && userPublicData.contactData && (
                    <Button
                        colorScheme="brand"
                        size="sm"
                        onClick={() =>
                            setEditModalData({
                                alias: userPublicData.contactData?.alias,
                                userId: userPublicData.userId,
                                nickName: userPublicData.nickName,
                            })
                        }
                    >
                        {t("user-public-page:edit-contact-button")}
                    </Button>
                )}
                {!self && userPublicData.contactData && (
                    <Button size="sm" colorScheme="red" onClick={removeContactConfirmation.onOpen}>
                        {t("user-public-page:remove-contact-button")}
                    </Button>
                )}
                {self && (
                    <Button colorScheme="brand" size="sm" onClick={() => navigate("/profile")}>
                        {t("user-public-page:go-to-profile-button")}
                    </Button>
                )}
            </Wrap>
        </Box>
    )
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    removeFromContacts: (userId: string) => dispatch(deleteContactAction.started(userId)),
})

export default connect(undefined, mapDispatchToProps)(withTranslation()(PublicUserDataActionsPanel))
