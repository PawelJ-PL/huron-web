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
    addMemberAction,
    changeInvitationAcceptanceAction,
    cleanCollectionDetailsAction,
    cleanCollectionKeyAction,
    clearSetMemberPermissionsResultAction,
    createCollectionAction,
    deleteCollectionAction,
    deleteMemberAction,
    fetchAndDecryptCollectionKeyAction,
    getCollectionDetailsAction,
    getCollectionMembersAction,
    getPreferredCollectionIdAction,
    listCollectionsAction,
    listMyPermissionsToCollectionActions,
    removePreferredCollectionIdAction,
    requestMemberDeleteAction,
    requestPermissionsChangeForMemberAction,
    RequestPermissionsUpdateParams,
    resetAddMemberResultAction,
    resetAvailableCollectionsListAction,
    resetCollectionMembersResultAction,
    resetCreateCollectionStatusAction,
    resetDeleteCollectionResultAction,
    resetDeleteMemberStatsAction,
    resetRemovePreferredCollectionResultAction,
    setActiveCollectionAction,
    setMemberPermissionsAction,
    setPreferredCollectionIdAction,
    updateCollectionsListFilter,
} from "./Actions"
import { CollectionsListFilter } from "../types/CollectionsListFilter"
import omit from "lodash/omit"

export const initialCollectionsListFilter: CollectionsListFilter = {
    nameFilter: "",
    acceptanceFilter: {
        showAccepted: true,
        showNonAccepted: true,
    },
}

const listCollectionsReducer = createReducer(listCollectionsAction, resetAvailableCollectionsListAction)
    .case(createCollectionAction.done, (state, action) => {
        if (state.status === "FINISHED") {
            const updated = state.data.concat(action.result)
            return { ...state, data: updated }
        } else {
            return state
        }
    })
    .case(changeInvitationAcceptanceAction.done, (state, action) => {
        if (state.status === "FINISHED") {
            const idx = state.data.findIndex((c) => c.id === action.params.collectionId)
            const newList = state.data.slice(0)
            newList[idx] = { ...state.data[idx], isAccepted: action.params.isAccepted }
            return { ...state, data: newList }
        }
        return state
    })
    .case(deleteCollectionAction.done, (state, action) => {
        if (state.status === "FINISHED") {
            const filteredData = state.data.filter((c) => c.id !== action.params)
            return { ...state, data: filteredData }
        }
        return state
    })
    .build()

const getCollectionReducer = createReducer(getCollectionDetailsAction, cleanCollectionDetailsAction)
    .case(changeInvitationAcceptanceAction.done, (state, action) => {
        if (state.status === "FINISHED" && state.data?.id === action.params.collectionId) {
            return { ...state, data: { ...state.data, isAccepted: action.params.isAccepted } }
        }
        return state
    })
    .case(deleteCollectionAction.done, (state, action) => {
        if (state.status !== "NOT_STARTED" && state.params === action.params) {
            return { status: "FINISHED", params: state.params, data: null }
        }
        return state
    })
    .build()

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
    .case(deleteCollectionAction.done, (state, action) => {
        if (state === action.params) {
            return null
        }
        return state
    })
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

const collectionsListFilterReducer = reducerWithInitialState<CollectionsListFilter>(initialCollectionsListFilter)
    .case(updateCollectionsListFilter, (state, action) => ({
        nameFilter: action.nameFilter ?? state.nameFilter,
        acceptanceFilter: {
            showAccepted: action.acceptanceFilter?.showAccepted ?? state.acceptanceFilter.showAccepted,
            showNonAccepted: action.acceptanceFilter?.showNonAccepted ?? state.acceptanceFilter.showNonAccepted,
        },
    }))
    .build()

const updateAcceptanceReducer = createReducer(changeInvitationAcceptanceAction).build()

const collectionMembersReducer = createReducer(getCollectionMembersAction, resetCollectionMembersResultAction)
    .case(addMemberAction.done, (state, action) => {
        if (state.status === "FINISHED" && state.params === action.params.collectionId) {
            const updated = { ...state.data, [action.params.userId]: action.params.permissions }
            return { ...state, data: updated }
        }
        return state
    })
    .case(deleteMemberAction.done, (state, action) => {
        if (state.status === "FINISHED" && state.params === action.params.collectionId) {
            const filtered = omit(state.data, action.params.memberId)
            return { ...state, data: filtered }
        }
        return state
    })
    .case(setMemberPermissionsAction.done, (state, action) => {
        if (state.status === "FINISHED" && state.params === action.params.collectionId) {
            const updated = { ...state.data, [action.params.memberId]: action.params.permissions }
            return { ...state, data: updated }
        }
        return state
    })
    .build()

const myPermissionsReducer = createReducer(listMyPermissionsToCollectionActions).build()

const deleteCollectionReducer = createReducer(deleteCollectionAction, resetDeleteCollectionResultAction).build()

const addCollectionMemberReducer = createReducer(addMemberAction, resetAddMemberResultAction, {
    params: (origParams) => ({ ...origParams, masterKey: "" }),
}).build()

const deleteMemberReducer = createReducer(deleteMemberAction, resetDeleteMemberStatsAction).build()

const requestDeleteMemberReducer = reducerWithInitialState<string | null>(null)
    .case(requestMemberDeleteAction, (_, action) => action)
    .build()

const setPermissionsReducer = createReducer(setMemberPermissionsAction, clearSetMemberPermissionsResultAction).build()

const requestPermissionUpdate = reducerWithInitialState<RequestPermissionsUpdateParams | null>(null)
    .case(requestPermissionsChangeForMemberAction, (_, action) => action)
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
    collectionsListFilter: collectionsListFilterReducer,
    updateAcceptanceResult: updateAcceptanceReducer,
    collectionMembers: collectionMembersReducer,
    myPermissions: myPermissionsReducer,
    deleteCollectionResult: deleteCollectionReducer,
    requestDeleteMember: requestDeleteMemberReducer,
    addMember: addCollectionMemberReducer,
    deleteMember: deleteMemberReducer,
    setPermissions: setPermissionsReducer,
    requestedPermissionUpdateForMember: requestPermissionUpdate,
})
