import { exampleHashedEmail, exampleMasterKey, exampleUserPassword } from "./../../../testutils/constants/user"
import {
    clearMasterKeyAction,
    computeMasterKeyAction,
    fetchAndDecryptKeyPairAction,
    localLogoutAction,
    resetKeyPairAction,
} from "./../../user/store/Actions"
import {
    exampleCollection,
    exampleCollectionId,
    exampleEncryptionKeyVersion,
} from "./../../../testutils/constants/collection"
import { exampleCollectionName } from "../../../testutils/constants/collection"
import {
    changeInvitationAcceptanceAction,
    createCollectionAction,
    setActiveCollectionAction,
    setPreferredCollectionIdAction,
    updateCollectionsListFilter,
} from "./Actions"
import { collectionsReducer } from "./Reducers"

type State = ReturnType<typeof collectionsReducer>

const defaultState = {} as State

describe("Collections reducers", () => {
    describe("list collections", () => {
        it("should add collection on create action success", () => {
            const collection1 = { ...exampleCollection, id: "a1490b4c-3f25-49f6-a4bb-6a2e7c7dd6ce", name: "col1" }
            const collection2 = { ...exampleCollection, id: "f75039b6-31e9-4b88-a03b-4fde25145831", name: "col2" }
            const collection3 = { ...exampleCollection, id: "dc9776d7-de4a-4d4e-9f16-fafc2e4b6fee", name: "col3" }
            const state: State = {
                ...defaultState,
                availableCollections: {
                    status: "FINISHED",
                    params: true,
                    data: [collection1, collection2, collection3],
                },
            }

            const action = createCollectionAction.done({ params: exampleCollectionName, result: exampleCollection })
            const result = collectionsReducer(state, action)
            expect(result.availableCollections).toStrictEqual({
                status: "FINISHED",
                params: true,
                data: [collection1, collection2, collection3, exampleCollection],
            })
        })

        it("should do nothing on create action success if previously not fetched", () => {
            const state: State = {
                ...defaultState,
                availableCollections: {
                    status: "FAILED",
                    params: true,
                    error: new Error("Some error"),
                },
            }

            const action = createCollectionAction.done({ params: exampleCollectionName, result: exampleCollection })
            const result = collectionsReducer(state, action)
            expect(result.availableCollections).toStrictEqual({
                status: "FAILED",
                params: true,
                error: new Error("Some error"),
            })
        })

        it("should update acceptance status", () => {
            const collection1 = { ...exampleCollection, id: "a1490b4c-3f25-49f6-a4bb-6a2e7c7dd6ce", name: "col1" }
            const collection2 = { ...exampleCollection, id: "f75039b6-31e9-4b88-a03b-4fde25145831", name: "col2" }
            const collection3 = { ...exampleCollection, id: "dc9776d7-de4a-4d4e-9f16-fafc2e4b6fee", name: "col3" }
            const state: State = {
                ...defaultState,
                availableCollections: {
                    status: "FINISHED",
                    params: true,
                    data: [collection1, collection2, collection3],
                },
            }

            const action = changeInvitationAcceptanceAction.done({
                params: { collectionId: "f75039b6-31e9-4b88-a03b-4fde25145831", isAccepted: false },
                result: undefined,
            })
            const result = collectionsReducer(state, action)
            expect(result.availableCollections).toStrictEqual({
                status: "FINISHED",
                params: true,
                data: [collection1, { ...collection2, isAccepted: false }, collection3],
            })
        })
    })

    describe("read preferred collection", () => {
        it("should update preferred collection when new value is set", () => {
            const state: State = defaultState
            const action = setPreferredCollectionIdAction.done({ params: "new-preferred-collection" })
            const result = collectionsReducer(state, action)
            expect(result.getPreferredCollectionResult).toStrictEqual({
                status: "FINISHED",
                params: undefined,
                data: "new-preferred-collection",
            })
        })
    })

    describe("set active collection", () => {
        it("should set new active collection", () => {
            const state: State = { ...defaultState, activeCollection: exampleCollectionId }
            const action = setActiveCollectionAction("second-collection")
            const result = collectionsReducer(state, action)
            expect(result.activeCollection).toBe("second-collection")
        })

        it("should remove active collection on local logout", () => {
            const state: State = { ...defaultState, activeCollection: exampleCollectionId }
            const action = localLogoutAction()
            const result = collectionsReducer(state, action)
            expect(result.activeCollection).toBeNull()
        })
    })

    describe("collection key", () => {
        it("should reset collection key on local logout action", () => {
            const state: State = {
                ...defaultState,
                encryptionKey: {
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: "" },
                    data: {
                        collectionId: exampleCollectionId,
                        version: exampleEncryptionKeyVersion,
                        key: "collection-secret",
                    },
                },
            }
            const action = localLogoutAction()
            const result = collectionsReducer(state, action)
            expect(result.encryptionKey).toEqual({ status: "NOT_STARTED" })
        })

        it("should reset collection key on keypair encrypt action failed", () => {
            const state: State = {
                ...defaultState,
                encryptionKey: {
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: "" },
                    data: {
                        collectionId: exampleCollectionId,
                        version: exampleEncryptionKeyVersion,
                        key: "collection-secret",
                    },
                },
            }
            const action = fetchAndDecryptKeyPairAction.failed({
                params: exampleMasterKey,
                error: new Error("Some error"),
            })
            const result = collectionsReducer(state, action)
            expect(result.encryptionKey).toEqual({ status: "NOT_STARTED" })
        })

        it("should reset collection key on keypair encrypt action started", () => {
            const state: State = {
                ...defaultState,
                encryptionKey: {
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: "" },
                    data: {
                        collectionId: exampleCollectionId,
                        version: exampleEncryptionKeyVersion,
                        key: "collection-secret",
                    },
                },
            }
            const action = fetchAndDecryptKeyPairAction.started(exampleMasterKey)
            const result = collectionsReducer(state, action)
            expect(result.encryptionKey).toEqual({ status: "NOT_STARTED" })
        })

        it("should reset collection key on keypair encrypt action cleared", () => {
            const state: State = {
                ...defaultState,
                encryptionKey: {
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: "" },
                    data: {
                        collectionId: exampleCollectionId,
                        version: exampleEncryptionKeyVersion,
                        key: "collection-secret",
                    },
                },
            }
            const action = resetKeyPairAction()
            const result = collectionsReducer(state, action)
            expect(result.encryptionKey).toEqual({ status: "NOT_STARTED" })
        })

        it("should reset collection key on compute master key action failed", () => {
            const state: State = {
                ...defaultState,
                encryptionKey: {
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: "" },
                    data: {
                        collectionId: exampleCollectionId,
                        version: exampleEncryptionKeyVersion,
                        key: "collection-secret",
                    },
                },
            }
            const action = computeMasterKeyAction.failed({
                params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
                error: new Error("Some error"),
            })
            const result = collectionsReducer(state, action)
            expect(result.encryptionKey).toEqual({ status: "NOT_STARTED" })
        })

        it("should reset collection key on compute master key action started", () => {
            const state: State = {
                ...defaultState,
                encryptionKey: {
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: "" },
                    data: {
                        collectionId: exampleCollectionId,
                        version: exampleEncryptionKeyVersion,
                        key: "collection-secret",
                    },
                },
            }
            const action = computeMasterKeyAction.started({
                password: exampleUserPassword,
                emailHash: exampleHashedEmail,
            })
            const result = collectionsReducer(state, action)
            expect(result.encryptionKey).toEqual({ status: "NOT_STARTED" })
        })

        it("should reset collection key on compute master key action cleared", () => {
            const state: State = {
                ...defaultState,
                encryptionKey: {
                    status: "FINISHED",
                    params: { collectionId: exampleCollectionId, privateKey: "" },
                    data: {
                        collectionId: exampleCollectionId,
                        version: exampleEncryptionKeyVersion,
                        key: "collection-secret",
                    },
                },
            }
            const action = clearMasterKeyAction()
            const result = collectionsReducer(state, action)
            expect(result.encryptionKey).toEqual({ status: "NOT_STARTED" })
        })
    })

    describe("get collection", () => {
        it("Should update acceptance status", () => {
            const state: State = {
                ...defaultState,
                collectionDetails: { status: "FINISHED", params: exampleCollectionId, data: exampleCollection },
            }
            const action = changeInvitationAcceptanceAction.done({
                params: { collectionId: exampleCollectionId, isAccepted: false },
                result: undefined,
            })
            const result = collectionsReducer(state, action)
            expect(result.collectionDetails).toStrictEqual({
                status: "FINISHED",
                params: exampleCollectionId,
                data: { ...exampleCollection, isAccepted: false },
            })
        })

        it("Should not update acceptance status if called for another collection", () => {
            const state: State = {
                ...defaultState,
                collectionDetails: { status: "FINISHED", params: exampleCollectionId, data: exampleCollection },
            }
            const action = changeInvitationAcceptanceAction.done({
                params: { collectionId: "other-collection", isAccepted: false },
                result: undefined,
            })
            const result = collectionsReducer(state, action)
            expect(result.collectionDetails).toStrictEqual(state.collectionDetails)
        })
    })

    describe("collection filter", () => {
        it("should update all filters", () => {
            const state: State = {
                ...defaultState,
                collectionsListFilter: {
                    acceptanceFilter: { showAccepted: true, showNonAccepted: true },
                    nameFilter: "foo",
                },
            }
            const action = updateCollectionsListFilter({
                acceptanceFilter: { showAccepted: false, showNonAccepted: false },
                nameFilter: "bar",
            })
            const result = collectionsReducer(state, action)
            expect(result.collectionsListFilter).toStrictEqual({
                acceptanceFilter: { showAccepted: false, showNonAccepted: false },
                nameFilter: "bar",
            })
        })

        it("should update partial filter", () => {
            const state: State = {
                ...defaultState,
                collectionsListFilter: {
                    acceptanceFilter: { showAccepted: true, showNonAccepted: true },
                    nameFilter: "foo",
                },
            }
            const action = updateCollectionsListFilter({
                acceptanceFilter: { showAccepted: false, showNonAccepted: false },
            })
            const result = collectionsReducer(state, action)
            expect(result.collectionsListFilter).toStrictEqual({
                acceptanceFilter: { showAccepted: false, showNonAccepted: false },
                nameFilter: "foo",
            })
        })
    })
})
