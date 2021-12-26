import { Icon, Input, InputGroup, InputLeftElement } from "@chakra-ui/react"
import React, { ChangeEvent, useCallback } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { FaFilter } from "react-icons/fa"
import debounce from "lodash/debounce"
import { Dispatch } from "redux"
import { refreshContactsListWithParamAction } from "../../store/Actions"
import { connect } from "react-redux"

type Props = {
    defaultValue?: string
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapDispatchToProps>

const FilterContactsInput: React.FC<Props> = ({ t, defaultValue, refreshContactWithNewFilter }) => {
    // eslint-disable-next-line react-hooks/exhaustive-deps
    const handler = useCallback(
        debounce((e: ChangeEvent<HTMLInputElement>) => refreshContactWithNewFilter(e.target.value), 500),
        [refreshContactWithNewFilter]
    )

    return (
        <InputGroup>
            <InputLeftElement pointerEvents="none">
                <Icon as={FaFilter} />
            </InputLeftElement>
            <Input
                borderColor="gray.400"
                width={["100%", null, "md", "2xl"]}
                placeholder={t("contacts-list:filter-input-placeholder")}
                defaultValue={defaultValue}
                autoFocus={true}
                onChange={handler}
            />
        </InputGroup>
    )
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    refreshContactWithNewFilter: (newFilter: string) => {
        const filterValue = newFilter.trim().length > 0 ? newFilter.trim() : null
        dispatch(refreshContactsListWithParamAction({ nameFilter: filterValue, page: 1 }))
    },
})

export default connect(undefined, mapDispatchToProps)(withTranslation()(FilterContactsInput))
