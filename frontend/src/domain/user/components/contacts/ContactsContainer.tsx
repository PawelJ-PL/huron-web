import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import Loader from "../../../../application/components/common/Loader"
import UnexpectedErrorMessage from "../../../../application/components/common/UnexpectedErrorMessage"
import { AppState } from "../../../../application/store"
import { listContactsAction, resetListContactsResultAction, updateContactsFilterAction } from "../../store/Actions"
import { ContactsFilter } from "../../types/ContactsFilter"
import ContactsPage from "./ContactsPage"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & Pick<WithTranslation, "t">

export const ContactsContainer: React.FC<Props> = ({
    contactsListResult,
    fetchContacts,
    resetContactsListResult,
    resetContactsFilter,
    t,
}) => {
    useEffect(() => {
        resetContactsFilter()
        fetchContacts({})
        return () => {
            resetContactsFilter()
            resetContactsListResult()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])
    if (contactsListResult.status === "FAILED") {
        return <UnexpectedErrorMessage error={contactsListResult.error} />
    } else if (contactsListResult.status === "FINISHED") {
        return <ContactsPage contacts={contactsListResult.data} appliedFilter={contactsListResult.params.nameFilter} />
    } else {
        return <Loader title={t("contacts-list:loader-text")} />
    }
}

const mapStateToProps = (state: AppState) => ({
    contactsListResult: state.users.contacts,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchContacts: (params: { page?: number; limit?: number; filters?: ContactsFilter }) =>
        dispatch(
            listContactsAction.started({ page: params.page, limit: params.limit, nameFilter: params.filters?.name })
        ),
    resetContactsListResult: () => dispatch(resetListContactsResultAction()),
    resetContactsFilter: () => dispatch(updateContactsFilterAction({ name: "" })),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(ContactsContainer))
