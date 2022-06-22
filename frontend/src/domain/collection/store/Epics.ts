import { EMPTY, of } from "rxjs"
import { AppState } from "./../../../application/store/index"
import { combineEpics, Epic } from "redux-observable"
import { Collection } from "./../types/Collection"
import { createEpic } from "../../../application/store/async/AsyncActionEpic"
import {
    addMemberAction,
    changeInvitationAcceptanceAction,
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
    setActiveCollectionAction,
    setMemberPermissionsAction,
    setPreferredCollectionIdAction,
} from "./Actions"
import CollectionsApi from "../api/CollectionsApi"
import { Action, AnyAction } from "redux"
import UsersApi from "../../user/api/UsersApi"
import CryptoApi from "../../../application/cryptography/api/CryptoApi"
import StorageApi from "../../../application/storage/StorageApi"
import { EncryptionKey } from "../types/EncryptionKey"
import { filter, mergeMap, map } from "rxjs/operators"
import { fetchAndDecryptKeyPairAction, localLogoutAction } from "../../user/store/Actions"

const PREFERRED_COLLECTION_KEY = "preferredCollectionId"

const readOrFetchKeypair = async (state: AppState, masterKey: string) => {
    if (state.users.keyPair.status === "FINISHED") {
        return state.users.keyPair.data
    }
    const encryptedKeyPair = await UsersApi.fetchKeyPair()
    const decryptedPrivateKey = await CryptoApi.decryptToString(encryptedKeyPair.encryptedPrivateKey, masterKey, false)
    return { publicKey: encryptedKeyPair.publicKey, privateKey: decryptedPrivateKey }
}

const listCollectionsEpic = createEpic<boolean, Collection[], Error>(listCollectionsAction, (onlyAccepted) =>
    CollectionsApi.listCollections(onlyAccepted)
)

const getCollectionEpic = createEpic<string, Collection | null, Error>(getCollectionDetailsAction, (collectionId) =>
    CollectionsApi.fetchCollection(collectionId)
)

const createCollectionEpic = createEpic<string, Collection, Error>(
    createCollectionAction,
    async (collectionName, state) => {
        const publicKey = await (state.users.keyPair.status === "FINISHED"
            ? Promise.resolve(state.users.keyPair.data.publicKey)
            : UsersApi.fetchKeyPair().then((keyPair) => keyPair.publicKey))
        const collectionPassword = await CryptoApi.randomBytes(32)
        const encryptedPassword = await CryptoApi.asymmetricEncrypt(collectionPassword, publicKey)
        return CollectionsApi.createCollection({ name: collectionName, encryptedKey: encryptedPassword })
    }
)

const readPreferredCollectionEpic = createEpic<void, string | null, Error>(getPreferredCollectionIdAction, () =>
    StorageApi.getItem(PREFERRED_COLLECTION_KEY)
)

const setPreferredCollectionEpic = createEpic<string, void, Error>(setPreferredCollectionIdAction, (collectionId) =>
    StorageApi.setItem(PREFERRED_COLLECTION_KEY, collectionId, "Local")
)

const removePreferredCollectionEpic = createEpic<void, void, Error>(removePreferredCollectionIdAction, () =>
    StorageApi.removeItem(PREFERRED_COLLECTION_KEY)
)

const removePreferredCollectionOnLocalLogoutEpic: Epic<AnyAction, AnyAction, AppState> = (action$) =>
    action$.pipe(
        filter(localLogoutAction.match),
        map(() => removePreferredCollectionIdAction.started())
    )

const fetchAndDecryptCollectionKeyEpic = createEpic<{ collectionId: string; privateKey: string }, EncryptionKey, Error>(
    fetchAndDecryptCollectionKeyAction,
    async (params) => {
        const encryptedData = await CollectionsApi.fetchEncryptionKey(params.collectionId)
        if (!encryptedData) {
            const failedResult: Promise<EncryptionKey> = Promise.reject(
                new Error(`Encryption key not set for collection ${params.collectionId}`)
            )
            return failedResult
        }
        const decryptedKey = await CryptoApi.asymmetricDecrypt(encryptedData.key, params.privateKey)
        return Promise.resolve({ collectionId: params.collectionId, key: decryptedKey, version: encryptedData.version })
    }
)

const fetchAndDecryptCollectionKeyOnKeypairDecryptedEpic: Epic<AnyAction, AnyAction, AppState> = (action$, state$) =>
    action$.pipe(
        filter(fetchAndDecryptKeyPairAction.done.match),
        mergeMap((action) => {
            const collection = state$.value.collections.activeCollection
            if (!collection) {
                return EMPTY
            } else {
                return of(
                    fetchAndDecryptCollectionKeyAction.started({
                        collectionId: collection,
                        privateKey: action.payload.result.privateKey,
                    })
                )
            }
        })
    )

const fetchAndDecryptCollectionKeyOnCollectionChange: Epic<AnyAction, AnyAction, AppState> = (action$, state$) =>
    action$.pipe(
        filter(setActiveCollectionAction.match),
        mergeMap((action) => {
            const privateKey =
                state$.value.users.keyPair.status === "FINISHED"
                    ? state$.value.users.keyPair.data.privateKey
                    : undefined
            if (!action.payload || !privateKey) {
                return EMPTY
            } else {
                return of(fetchAndDecryptCollectionKeyAction.started({ collectionId: action.payload, privateKey }))
            }
        })
    )

const updateAcceptanceEpic = createEpic(changeInvitationAcceptanceAction, ({ collectionId, isAccepted }) => {
    if (isAccepted) {
        return CollectionsApi.acceptInvitation(collectionId)
    } else {
        return CollectionsApi.cancelInvitationAcceptance(collectionId)
    }
})

const getCollectionMembersEpic = createEpic(getCollectionMembersAction, (collectionId) =>
    CollectionsApi.getCollectionMembers(collectionId)
)

const listMyPermissionsEpic = createEpic(listMyPermissionsToCollectionActions, (collectionId, state) => {
    if (state.users.userData.status !== "FINISHED") {
        return Promise.reject(new Error("User data not loaded yet"))
    }
    const myId = state.users.userData.data.id
    return CollectionsApi.getMemberPermissions(collectionId, myId)
})

const deleteCollectionEpic = createEpic(deleteCollectionAction, CollectionsApi.deleteCollection)

const deletePreferredCollectionOnCollectionRemoveEpic: Epic<AnyAction, AnyAction, AppState> = (action$, state$) =>
    action$.pipe(
        filter(deleteCollectionAction.done.match),
        mergeMap((action) => {
            const preferredCollectionResult = state$.value.collections.getPreferredCollectionResult
            const maybePreferredCollection =
                preferredCollectionResult.status === "FINISHED" ? preferredCollectionResult.data : null
            const setPreferredCollectionResult = state$.value.collections.setPreferredCollectionResult
            const setPreferredCollectionParams =
                setPreferredCollectionResult.status !== "NOT_STARTED" ? setPreferredCollectionResult.params : null
            if (
                action.payload.params === maybePreferredCollection ||
                action.payload.params === setPreferredCollectionParams
            ) {
                return of(removePreferredCollectionIdAction.started())
            } else {
                return EMPTY
            }
        })
    )

const addMemberEpic = createEpic(addMemberAction, async ({ collectionId, userId, permissions, masterKey }, state) => {
    const collectionKey = await CollectionsApi.fetchEncryptionKey(collectionId)
    if (!collectionKey) {
        const failedResult: Promise<never> = Promise.reject(
            new Error(`Encryption key not set for collection ${collectionId}`)
        )
        return failedResult
    }
    const keyPair =
        state.users.keyPair.status === "FINISHED"
            ? state.users.keyPair.data
            : await readOrFetchKeypair(state, masterKey)
    const decryptedKey = await CryptoApi.asymmetricDecrypt(collectionKey.key, keyPair.privateKey)
    const targetUserPublicKey = await UsersApi.getPublicKey(userId)
    if (!targetUserPublicKey) {
        const failedResult: Promise<never> = Promise.reject(new Error("User has no public key"))
        return failedResult
    }
    const encryptedCollectionKey = await CryptoApi.asymmetricEncrypt(decryptedKey, targetUserPublicKey.publicKey)
    await CollectionsApi.addMember({
        collectionId,
        userId,
        collectionKeyVersion: collectionKey.version,
        encryptedCollectionKey,
        permissions,
    })
})

const deleteMemberEpic = createEpic(deleteMemberAction, ({ memberId, collectionId }) =>
    CollectionsApi.deleteMember(collectionId, memberId)
)

const resetCollectionOnLeaveEpic: Epic<AnyAction, AnyAction, AppState> = (action$, state$) =>
    action$.pipe(
        filter(deleteMemberAction.done.match),
        mergeMap((action) => {
            const currentCollectionResult = state$.value.collections.collectionDetails
            if (currentCollectionResult.status !== "FINISHED") {
                return EMPTY
            }
            const currentCollection = currentCollectionResult.data?.id
            const currentUser =
                state$.value.users.userData.status === "FINISHED" ? state$.value.users.userData.data.id : undefined
            if (
                action.payload.params.collectionId === currentCollection &&
                action.payload.params.memberId === currentUser
            ) {
                return of(getCollectionDetailsAction.done({ params: currentCollectionResult.params, result: null }))
            }
            return EMPTY
        })
    )

const setPermissionsEpic = createEpic(setMemberPermissionsAction, ({ collectionId, memberId, permissions }) =>
    CollectionsApi.setMemberPermissions(collectionId, memberId, permissions)
)

const updateMyPermissionsEpic: Epic<AnyAction, AnyAction, AppState> = (action$, state$) =>
    action$.pipe(
        filter(setMemberPermissionsAction.done.match),
        mergeMap((action) => {
            const myId =
                state$.value.users.userData.status === "FINISHED" ? state$.value.users.userData.data.id : undefined
            const collectionIdForPermissions =
                state$.value.collections.myPermissions.status !== "NOT_STARTED"
                    ? state$.value.collections.myPermissions.params
                    : undefined
            if (
                action.payload.params.collectionId === collectionIdForPermissions &&
                action.payload.params.memberId === myId
            ) {
                return of(
                    listMyPermissionsToCollectionActions.done({
                        params: action.payload.params.collectionId,
                        result: action.payload.params.permissions,
                    })
                )
            }
            return EMPTY
        })
    )

export const collectionsEpics = combineEpics<Action, Action, AppState>(
    listCollectionsEpic,
    getCollectionEpic,
    createCollectionEpic,
    readPreferredCollectionEpic,
    setPreferredCollectionEpic,
    removePreferredCollectionEpic,
    removePreferredCollectionOnLocalLogoutEpic,
    fetchAndDecryptCollectionKeyEpic,
    fetchAndDecryptCollectionKeyOnKeypairDecryptedEpic,
    fetchAndDecryptCollectionKeyOnCollectionChange,
    updateAcceptanceEpic,
    getCollectionMembersEpic,
    listMyPermissionsEpic,
    deleteCollectionEpic,
    deletePreferredCollectionOnCollectionRemoveEpic,
    addMemberEpic,
    deleteMemberEpic,
    resetCollectionOnLeaveEpic,
    setPermissionsEpic,
    updateMyPermissionsEpic
)
