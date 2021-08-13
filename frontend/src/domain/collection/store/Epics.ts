import { EMPTY, of } from "rxjs"
import { AppState } from "./../../../application/store/index"
import { combineEpics, Epic } from "redux-observable"
import { Collection } from "./../types/Collection"
import { createEpic } from "../../../application/store/async/AsyncActionEpic"
import {
    createCollectionAction,
    fetchAndDecryptCollectionKeyAction,
    getCollectionDetailsAction,
    getPreferredCollectionIdAction,
    listCollectionsAction,
    removePreferredCollectionIdAction,
    setActiveCollectionAction,
    setPreferredCollectionIdAction,
} from "./Actions"
import CollectionsApi from "../api/CollectionsApi"
import { Action, AnyAction } from "redux"
import UsersApi from "../../user/api/UsersApi"
import CryptoApi from "../../../application/cryptography/api/CryptoApi"
import StorageApi from "../../../application/storage/StorageApi"
import { EncryptionKey } from "../types/EncryptionKey"
import { filter, mergeMap } from "rxjs/operators"
import { fetchAndDecryptKeyPairAction } from "../../user/store/Actions"

const PREFERRED_COLLECTION_KEY = "preferredCollectionId"

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

export const collectionsEpics = combineEpics<Action, Action, AppState>(
    listCollectionsEpic,
    getCollectionEpic,
    createCollectionEpic,
    readPreferredCollectionEpic,
    setPreferredCollectionEpic,
    removePreferredCollectionEpic,
    fetchAndDecryptCollectionKeyEpic,
    fetchAndDecryptCollectionKeyOnKeypairDecryptedEpic,
    fetchAndDecryptCollectionKeyOnCollectionChange
)
