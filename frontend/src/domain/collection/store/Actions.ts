import { EncryptionKey } from "./../types/EncryptionKey"
import { Collection } from "./../types/Collection"
import { actionCreatorFactory } from "typescript-fsa"
import { CollectionsListFilter } from "../types/CollectionsListFilter"
const actionCreator = actionCreatorFactory("COLLECTION")

export const listCollectionsAction = actionCreator.async<boolean, Collection[], Error>("LIST_COLLECTIONS")
export const resetAvailableCollectionsListAction = actionCreator("RESET_AVAILABLE_COLLECTION_LIST")
export const getCollectionDetailsAction = actionCreator.async<string, Collection | null, Error>(
    "GET_COLLECTION_DETAILS"
)
export const cleanCollectionDetailsAction = actionCreator("CLEAN_COLLECTION_DETAILS")
export const createCollectionAction = actionCreator.async<string, Collection, Error>("CREATE_COLLECTION")
export const resetCreateCollectionStatusAction = actionCreator("RESET_CREATE_COLLECTION_STATUS")
export const getPreferredCollectionIdAction = actionCreator.async<void, string | null, Error>(
    "GET_PREFERRED_COLLECTION"
)
export const removePreferredCollectionIdAction = actionCreator.async<void, void, Error>("REMOVE_PREFERRED_COLLECTION")
export const resetRemovePreferredCollectionResultAction = actionCreator("RESET_REMOVE_PREFERRED_COLLECTION_RESULT")
export const setPreferredCollectionIdAction = actionCreator.async<string, void, Error>("SET_PREFERRED_COLLECTION_ID")
export const setActiveCollectionAction = actionCreator<string | null>("SET_ACTIVE_COLLECTION")
export const fetchAndDecryptCollectionKeyAction = actionCreator.async<
    { collectionId: string; privateKey: string },
    EncryptionKey,
    Error
>("FETCH_AND_DECRYPT_COLLECTION_KEY")
export const cleanCollectionKeyAction = actionCreator("CLEAN_COLLECTION_KEY")
export const updateCollectionsListFilter = actionCreator<Partial<CollectionsListFilter>>(
    "UPDATE_COLLECTIONS_LIST_FILTER"
)
export const changeInvitationAcceptanceAction = actionCreator.async<
    { collectionId: string; isAccepted: boolean },
    void,
    Error
>("CHANGE_INVITATION_ACCEPTANCE")
