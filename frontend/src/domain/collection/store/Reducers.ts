import {
    clearMasterKeyAction,
    computeMasterKeyAction,
    fetchAndDecryptKeyPairAction,
    localLogoutAction,
    resetKeyPairAction,
} from "./../../user/store/Actions"
import { combineReducers } from "redux"
import { reducerWithInitialState } from "typescript-fsa-reducers"
import { createReducer } from "../../../application/store/async/AsyncActionReducer"
import {
    cleanCollectionDetailsAction,
    cleanCollectionKeyAction,
    createCollectionAction,
    fetchAndDecryptCollectionKeyAction,
    getCollectionDetailsAction,
    getPreferredCollectionIdAction,
    listCollectionsAction,
    removePreferredCollectionIdAction,
    resetAvailableCollectionsListAction,
    resetCreateCollectionStatusAction,
    resetRemovePreferredCollectionResultAction,
    setActiveCollectionAction,
    setPreferredCollectionIdAction,
} from "./Actions"

const listCollectionsReducer = createReducer(listCollectionsAction, resetAvailableCollectionsListAction)
    .case(createCollectionAction.done, (state, action) => {
        if (state.status === "FINISHED") {
            const updated = state.data.concat(action.result)
            return { ...state, data: updated }
        } else {
            return state
        }
    })
    .build()

const getCollectionReducer = createReducer(getCollectionDetailsAction, cleanCollectionDetailsAction).build()

const createCollectionReducer = createReducer(createCollectionAction, resetCreateCollectionStatusAction).build()

const readPreferredCollectionReducer = createReducer(getPreferredCollectionIdAction)
    .case(setPreferredCollectionIdAction.done, (_, action) => ({
        status: "FINISHED",
        data: action.params,
        params: undefined,
    }))
    .case(removePreferredCollectionIdAction.done, () => ({ status: "NOT_STARTED" }))
    .build()

const removePreferredCollectionReducer = createReducer(
    removePreferredCollectionIdAction,
    resetRemovePreferredCollectionResultAction
).build()

const setPreferredCollectionReducer = createReducer(setPreferredCollectionIdAction).build()

const activeCollectionReducer = reducerWithInitialState<string | null>(null)
    .case(setActiveCollectionAction, (_, action) => action)
    .case(localLogoutAction, () => null)
    .build()

const encryptionKeyReducer = createReducer(fetchAndDecryptCollectionKeyAction, cleanCollectionKeyAction, {
    params: ({ collectionId }) => ({ collectionId, privateKey: "" }),
})
    .cases(
        [
            localLogoutAction,
            fetchAndDecryptKeyPairAction.failed,
            fetchAndDecryptKeyPairAction.started,
            resetKeyPairAction,
        ],
        () => ({
            status: "NOT_STARTED",
        })
    )
    .cases([computeMasterKeyAction.failed, computeMasterKeyAction.started, clearMasterKeyAction], () => ({
        status: "NOT_STARTED",
    }))
    .build()

export const collectionsReducer = combineReducers({
    availableCollections: listCollectionsReducer,
    collectionDetails: getCollectionReducer,
    createCollectionResult: createCollectionReducer,
    getPreferredCollectionResult: readPreferredCollectionReducer,
    setPreferredCollectionResult: setPreferredCollectionReducer,
    removePreferredCollectionResult: removePreferredCollectionReducer,
    activeCollection: activeCollectionReducer,
    encryptionKey: encryptionKeyReducer,
})
