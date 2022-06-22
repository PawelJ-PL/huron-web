import { Button, Flex, HStack, Select, Stack, Text, useDisclosure } from "@chakra-ui/react"
import React from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { refreshContactsListWithParamAction } from "../../store/Actions"
import FilterContactsInput from "./FilterContactsInput"
import { FaUserPlus } from "react-icons/fa"
import FindUserModal from "./FindUserModal"
import AddOrUpdateContactModal from "../public_data/AddOrUpdateContactModal"
import { useState } from "react"

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
    const [requestedContactId, setRequestedContactId] = useState<string | null>(null)

    const limitValues = Array.from(new Set([5, 10, 30, 100, defaultEntries])).sort((a, b) => a - b)
    const findUserDisclosure = useDisclosure()

    return (
        <Flex
            alignItems={["flex-start", null, "center"]}
            justifyContent="space-between"
            direction={["column", null, "row"]}
        >
            <FindUserModal
                isOpen={findUserDisclosure.isOpen}
                onClose={findUserDisclosure.onClose}
                onSelect={(user) => setRequestedContactId(user.userId)}
                excludeContacts={true}
            />
            {requestedContactId && (
                <AddOrUpdateContactModal
                    isOpen={requestedContactId !== null}
                    onClose={() => setRequestedContactId(null)}
                    contactData={{ userId: requestedContactId }}
                />
            )}
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
                <Text flexShrink={0} fontSize="md">
                    {t("common:pagination.entries-per-page")}
                </Text>
            </HStack>
            <Stack direction={["column", null, "row"]} spacing="0.2rem">
                <FilterContactsInput defaultValue={defaultFilterValue} />
                <Button colorScheme="brand" leftIcon={<FaUserPlus />} onClick={findUserDisclosure.onOpen}>
                    {t("contacts-list:add-contact-button-label")}
                </Button>
            </Stack>
        </Flex>
    )
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    refreshUsersListWithNewLimit: (limit: number) => dispatch(refreshContactsListWithParamAction({ limit })),
})

export default connect(undefined, mapDispatchToProps)(withTranslation()(DisplayOptionsPanel))
