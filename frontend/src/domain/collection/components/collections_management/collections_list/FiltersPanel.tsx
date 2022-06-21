import { FormControl, FormHelperText, Input, Select, Stack } from "@chakra-ui/react"
import React, { useEffect } from "react"
import { ChangeEvent } from "react"
import { Trans, WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { AppState } from "../../../../../application/store"
import { updateCollectionsListFilter } from "../../../store/Actions"
import { initialCollectionsListFilter } from "../../../store/Reducers"
import { CollectionsListFilter } from "../../../types/CollectionsListFilter"

type Props = Pick<WithTranslation, "t"> & ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps>

const statusFilterToValue = (filter: CollectionsListFilter["acceptanceFilter"]) => {
    if (filter.showAccepted && filter.showNonAccepted) {
        return "showAll"
    }
    return filter.showAccepted ? "showAccepted" : "showNonAccepted"
}

const FiltersPanel: React.FC<Props> = ({ t, currentStatusFilter, resetFilters, updateFilters }) => {
    useEffect(() => {
        resetFilters()
        return () => {
            resetFilters()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const onNameFilterChange = (event: ChangeEvent<HTMLInputElement>) =>
        updateFilters({ nameFilter: event.target.value })

    const onStatusFilterChange = (event: ChangeEvent<HTMLSelectElement>) =>
        updateFilters({
            acceptanceFilter: {
                showAccepted: ["showAll", "showAccepted"].includes(event.target.value),
                showNonAccepted: ["showAll", "showNonAccepted"].includes(event.target.value),
            },
        })

    return (
        <Stack direction={["column", "row"]} spacing={["1rem", "1.5rem"]} marginBottom={["2.5rem", "0rem"]}>
            <FormControl>
                <Select defaultValue={statusFilterToValue(currentStatusFilter)} onChange={onStatusFilterChange}>
                    <option value="showAll">{t("collections-list-page:filter-by-status-options.all")}</option>
                    <option value="showAccepted">
                        {t("collections-list-page:filter-by-status-options.only-accepted")}
                    </option>
                    <option value="showNonAccepted">
                        {t("collections-list-page:filter-by-status-options.only-non-accepted")}
                    </option>
                </Select>
                <FormHelperText>
                    <Trans ns="collections-list-page" i18nKey="filter-by-status">
                        <b>Search</b> by name
                    </Trans>
                </FormHelperText>
            </FormControl>
            <FormControl>
                <Input onChange={onNameFilterChange} />
                <FormHelperText>
                    <Trans ns="collections-list-page" i18nKey="search-by-name">
                        <b>Search</b> by name
                    </Trans>
                </FormHelperText>
            </FormControl>
        </Stack>
    )
}

const mapStateToProps = (state: AppState) => ({
    currentStatusFilter: state.collections.collectionsListFilter.acceptanceFilter,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    resetFilters: () => dispatch(updateCollectionsListFilter(initialCollectionsListFilter)),
    updateFilters: (update: Partial<CollectionsListFilter>) => dispatch(updateCollectionsListFilter(update)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(FiltersPanel))
