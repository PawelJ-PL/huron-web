import { AlertProps, Box } from "@chakra-ui/react"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { Pagination } from "../../../../application/api/Pagination"
import Confirmation from "../../../../application/components/common/Confirmation"
import EmptyPlaceholder from "../../../../application/components/common/EmptyPlaceholder"
import LoadingOverlay from "../../../../application/components/common/LoadingOverlay"
import Paginator from "../../../../application/components/common/Paginator"
import UnexpectedErrorMessage from "../../../../application/components/common/UnexpectedErrorMessage"
import { AppState } from "../../../../application/store"
import {
    deleteContactAction,
    refreshContactsListWithParamAction,
    requestContactDeleteAction,
    requestContactEditAction,
    resetDeleteContactResultAction,
    resetEditContactResultAction,
} from "../../store/Actions"
import { UserContact } from "../../types/UserContact"
import ContactsListTable from "./ContactsListTable"
import DisplayOptionsPanel from "./DisplayOptionsPanel"
import { FaUsers } from "react-icons/fa"
import AddOrUpdateContactModal from "../public_data/AddOrUpdateContactModal"
import { ContactWithAliasAlreadyExists } from "../../api/errors"
import AlertBox from "../../../../application/components/common/AlertBox"

type Props = {
    contacts: Pagination<UserContact[]>
    appliedFilter?: string
} & ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps> &
    Pick<WithTranslation, "t">

const ContactsPage: React.FC<Props> = ({
    contacts,
    contactRequestedToDelete,
    unsetContactToDelete,
    deleteContact,
    t,
    actionInProgress,
    deleteContactResult,
    resetDeleteContactResult,
    appliedFilter,
    loadPage,
    contactRequestedToEdit,
    unsetContactToEdit,
    editContactResult,
    resetEditContactResult,
}) => {
    const resetStatuses = () => {
        resetDeleteContactResult()
    }

    useEffect(() => {
        resetStatuses()
        return () => {
            resetStatuses()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const renderError = (error: Error, onClose?: () => void) => {
        const alertProps: AlertProps = { marginBottom: "0.3rem" }

        if (error instanceof ContactWithAliasAlreadyExists) {
            return (
                <AlertBox
                    alertProps={alertProps}
                    icon={true}
                    onClose={onClose}
                    status="warning"
                    title={t("user-public-page:alias-already-exists-message", { alias: error.alias })}
                />
            )
        }
        return <UnexpectedErrorMessage error={error} onClose={onClose} alertProps={alertProps} />
    }

    return (
        <Box>
            {deleteContactResult.status === "FAILED" &&
                renderError(deleteContactResult.error, resetDeleteContactResult)}
            {editContactResult.status === "FAILED" && renderError(editContactResult.error, resetEditContactResult)}
            <LoadingOverlay active={actionInProgress} text={t("common:action-in-progress")} />
            {contactRequestedToDelete !== null && (
                <Confirmation
                    isOpen={true}
                    onClose={unsetContactToDelete}
                    onConfirm={() => deleteContact(contactRequestedToDelete.userId)}
                    content={t("user-public-page:remove-contact-confirmation.content", {
                        name: contactRequestedToDelete.alias ?? contactRequestedToDelete.nickName,
                    })}
                    title={t("user-public-page:remove-contact-confirmation.header")}
                />
            )}
            {contactRequestedToEdit && (
                <AddOrUpdateContactModal
                    isOpen={true}
                    onClose={unsetContactToEdit}
                    contactData={contactRequestedToEdit}
                />
            )}
            <DisplayOptionsPanel defaultEntries={contacts.elementsPerPage} defaultFilterValue={appliedFilter} />
            {contacts.result.length < 1 && (
                <EmptyPlaceholder text={t("contacts-list:no-contacts-placeholder")} icon={FaUsers} />
            )}
            <ContactsListTable contacts={contacts.result} />
            {contacts.totalPages > 1 && (
                <Paginator pagination={contacts} onPageChange={loadPage} containerProps={{ marginTop: "0.3rem" }} />
            )}
        </Box>
    )
}

const mapStateToProps = (state: AppState) => ({
    actionInProgress: [state.users.deleteContactResult.status, state.users.editContactResult.status].includes(
        "PENDING"
    ),
    contactRequestedToDelete: state.users.contactRequestedToDelete,
    deleteContactResult: state.users.deleteContactResult,
    contactRequestedToEdit: state.users.contactRequestedToEdit,
    editContactResult: state.users.editContactResult,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    unsetContactToDelete: () => dispatch(requestContactDeleteAction(null)),
    deleteContact: (userId: string) => dispatch(deleteContactAction.started(userId)),
    resetDeleteContactResult: () => dispatch(resetDeleteContactResultAction()),
    loadPage: (page: number) => dispatch(refreshContactsListWithParamAction({ page })),
    unsetContactToEdit: () => dispatch(requestContactEditAction(null)),
    resetEditContactResult: () => dispatch(resetEditContactResultAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(ContactsPage))
