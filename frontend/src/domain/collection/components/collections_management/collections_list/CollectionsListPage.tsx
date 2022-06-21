import { Box, Button, Stack, Table, Tbody, Th, Thead, Tr, useDisclosure, useToast } from "@chakra-ui/react"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import LoadingOverlay from "../../../../../application/components/common/LoadingOverlay"
import { AppState } from "../../../../../application/store"
import { Collection } from "../../../types/Collection"
import AutoCreateCollectionModal from "../../AutoCreateCollectionModal"
import CollectionListEntry from "./CollectionListEntry"
import FiltersPanel from "./FiltersPanel"

type Props = { collections: Collection[] } & Pick<WithTranslation, "t"> & ReturnType<typeof mapStateToProps>

const UPDATE_FAILED_TOAST_ID = "update-failed"

export const CollectionsListPage: React.FC<Props> = ({
    t,
    collections,
    listFilter,
    updatingDetails,
    updateDetailsFailed,
}) => {
    const filterByName = (collection: Collection) =>
        !listFilter.nameFilter || collection.name.toLowerCase().includes(listFilter.nameFilter.toLocaleLowerCase())

    const filterByAcceptance = (collection: Collection) =>
        (listFilter.acceptanceFilter.showAccepted && collection.isAccepted) ||
        (listFilter.acceptanceFilter.showNonAccepted && !collection.isAccepted)

    const filteredCollections = collections.filter(
        (collection) => filterByName(collection) && filterByAcceptance(collection)
    )

    const createCollectionModal = useDisclosure()

    const toast = useToast({ position: "top", isClosable: true })

    useEffect(() => {
        if (updateDetailsFailed && !toast.isActive(UPDATE_FAILED_TOAST_ID)) {
            toast({
                title: t("collections-list-page:updating-details-failed-toast-message"),
                status: "error",
                isClosable: true,
                id: UPDATE_FAILED_TOAST_ID,
            })
        }
    }, [t, toast, updateDetailsFailed])

    return (
        <Box>
            <LoadingOverlay text={t("collections-list-page:updating-details-overlay-text")} active={updatingDetails} />
            <Stack direction={["column", "row"]} alignItems="baseline" justifyContent="space-between">
                <Box>
                    <AutoCreateCollectionModal
                        isOpen={createCollectionModal.isOpen}
                        onClose={createCollectionModal.onClose}
                    />
                    <Button colorScheme="brand" onClick={() => createCollectionModal.onOpen()}>
                        {t("collections-list-page:create-collection-button")}
                    </Button>
                </Box>
                <Box float="right" minWidth={["100%", "0"]}>
                    <FiltersPanel />
                </Box>
            </Stack>
            <Table variant="simple" size={["sm", null, "md"]}>
                <Thead>
                    <Tr>
                        <Th></Th>
                        <Th>{t("collections-list-page:name")}</Th>
                        <Th>{t("collections-list-page:owner")}</Th>
                    </Tr>
                </Thead>
                <Tbody>
                    {filteredCollections.map((c) => (
                        <CollectionListEntry key={c.id} collection={c} />
                    ))}
                </Tbody>
            </Table>
        </Box>
    )
}

const mapStateToProps = (state: AppState) => ({
    listFilter: state.collections.collectionsListFilter,
    updatingDetails: state.collections.updateAcceptanceResult.status === "PENDING",
    updateDetailsFailed: state.collections.updateAcceptanceResult.status === "FAILED",
})

export default connect(mapStateToProps)(withTranslation()(CollectionsListPage))
