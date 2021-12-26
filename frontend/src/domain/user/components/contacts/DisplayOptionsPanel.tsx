import { Box, Flex, HStack, Select, Text } from "@chakra-ui/react"
import React from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { refreshContactsListWithParamAction } from "../../store/Actions"
import FilterContactsInput from "./FilterContactsInput"

type Props = {
    defaultEntries: number
    defaultFilterValue?: string
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapDispatchToProps>

const DisplayOptionsPanel: React.FC<Props> = ({
    t,
    defaultEntries,
    refreshUsersListWithNewLimit,
    defaultFilterValue,
}) => {
    const limitValues = Array.from(new Set([5, 10, 30, 100, defaultEntries])).sort((a, b) => a - b)

    return (
        <Flex
            alignItems={["flex-start", null, "center"]}
            justifyContent="space-between"
            direction={["column", null, "row"]}
        >
            <HStack alignItems="center" marginBottom={["0.2rem", null, "0"]}>
                <Select
                    defaultValue={defaultEntries}
                    borderColor="gray.400"
                    onChange={(e) => refreshUsersListWithNewLimit(Number(e.target.value))}
                >
                    {limitValues.map((v) => (
                        <option key={v} value={v}>
                            {v}
                        </option>
                    ))}
                </Select>
                <Text flexShrink="0" fontSize="md">
                    {t("common:pagination.entries-per-page")}
                </Text>
            </HStack>
            <Box>
                <FilterContactsInput defaultValue={defaultFilterValue} />
            </Box>
        </Flex>
    )
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    refreshUsersListWithNewLimit: (limit: number) => dispatch(refreshContactsListWithParamAction({ limit })),
})

export default connect(undefined, mapDispatchToProps)(withTranslation()(DisplayOptionsPanel))
