import { fetchAndDecryptKeyPairAction, localLogoutAction } from "./../../user/store/Actions"
import {
    exampleEncryptedKeypair,
    exampleMasterKey,
    examplePrivateKey,
    examplePublicKey,
} from "./../../../testutils/constants/user"
import { AppState } from "../../../application/store"
import { runAsyncEpic, verifyEpic } from "../../../testutils/epicsUtils"
import {
    exampleCollection,
    exampleCollectionId,
    exampleCollectionName,
} from "./../../../testutils/constants/collection"
import {
    changeInvitationAcceptanceAction,
    createCollectionAction,
    fetchAndDecryptCollectionKeyAction,
    removePreferredCollectionIdAction,
    setActiveCollectionAction,
} from "./Actions"
import { collectionsEpics } from "./Epics"
import CryptoApi from "../../../application/cryptography/api/CryptoApi"
import CollectionsApi from "../api/CollectionsApi"
import UsersApi from "../../user/api/UsersApi"

describe("Collections epics", () => {
    beforeEach(() => {
        jest.spyOn(CryptoApi, "asymmetricEncrypt").mockImplementation((input: string, publicKeyPem: string) => {
            return Promise.resolve(`encryptedAsym(${input} : ${publicKeyPem})`)
        })

        jest.spyOn(CryptoApi, "randomBytes").mockImplementation((length: number) => {
            return Promise.resolve(`random(${length})`)
        })
    })

    afterEach(() => {
        jest.restoreAllMocks()
    })

    it("should trigger collection create with key encrypted using local public key", async () => {
        const createCollectionSpy = jest.spyOn(CollectionsApi, "createCollection").mockResolvedValue(exampleCollection)
        const fetchKeyPairSpy = jest.spyOn(UsersApi, "fetchKeyPair").mockResolvedValue(exampleEncryptedKeypair)
        const state = {
            users: {
                keyPair: {
                    status: "FINISHED",
                    params: "",
                    data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
                },
            },
        } as AppState
        const trigger = createCollectionAction.started(exampleCollectionName)
        const result = await runAsyncEpic(trigger, collectionsEpics, state)
        expect(result).toStrictEqual(
            createCollectionAction.done({ params: exampleCollectionName, result: exampleCollection })
        )
        expect(createCollectionSpy).toHaveBeenCalledTimes(1)
        expect(createCollectionSpy).toHaveBeenCalledWith({
            encryptedKey: `encryptedAsym(random(32) : ${examplePublicKey})`,
            name: exampleCollectionName,
        })
        expect(fetchKeyPairSpy).not.toHaveBeenCalled()
    })

    it("should trigger collection create with key encrypted using fetched public key", async () => {
        const createCollectionSpy = jest.spyOn(CollectionsApi, "createCollection").mockResolvedValue(exampleCollection)
        const fetchKeyPairSpy = jest.spyOn(UsersApi, "fetchKeyPair").mockResolvedValue(exampleEncryptedKeypair)
        const state = {
            users: {
                keyPair: {
                    status: "NOT_STARTED",
                },
            },
        } as AppState
        const trigger = createCollectionAction.started(exampleCollectionName)
        const result = await runAsyncEpic(trigger, collectionsEpics, state)
        expect(result).toStrictEqual(
            createCollectionAction.done({ params: exampleCollectionName, result: exampleCollection })
        )
        expect(createCollectionSpy).toHaveBeenCalledTimes(1)
        expect(createCollectionSpy).toHaveBeenCalledWith({
            encryptedKey: `encryptedAsym(random(32) : ${examplePublicKey})`,
            name: exampleCollectionName,
        })
        expect(fetchKeyPairSpy).toHaveBeenCalledTimes(1)
    })

    it("should trigger collection fetch and decrypt when keypair decrypted", () => {
        const trigger = fetchAndDecryptKeyPairAction.done({
            params: exampleMasterKey,
            result: { privateKey: examplePrivateKey, publicKey: examplePublicKey },
        })
        const state = { collections: { activeCollection: exampleCollectionId } } as AppState
        const expectedAction = fetchAndDecryptCollectionKeyAction.started({
            collectionId: exampleCollectionId,
            privateKey: examplePrivateKey,
        })
        verifyEpic(trigger, collectionsEpics, state, { marbles: "-a", values: { a: expectedAction } })
    })

    it("should not trigger collection fetch and decrypt when keypair decrypted if active collection is not set", () => {
        const trigger = fetchAndDecryptKeyPairAction.done({
            params: exampleMasterKey,
            result: { privateKey: examplePrivateKey, publicKey: examplePublicKey },
        })
        const state = { collections: { activeCollection: null } } as AppState
        verifyEpic(trigger, collectionsEpics, state, { marbles: "---" })
    })

    it("should trigger collection fetch and decrypt when collection changed", () => {
        const trigger = setActiveCollectionAction("second-collection")
        const state = {
            collections: { activeCollection: null },
            users: {
                keyPair: {
                    status: "FINISHED",
                    params: "",
                    data: { privateKey: examplePrivateKey, publicKey: examplePublicKey },
                },
            },
        } as AppState
        const expectedAction = fetchAndDecryptCollectionKeyAction.started({
            collectionId: "second-collection",
            privateKey: examplePrivateKey,
        })
        verifyEpic(trigger, collectionsEpics, state, { marbles: "-a", values: { a: expectedAction } })
    })

    it("should not trigger collection fetch and decrypt when active collection unset", () => {
        const trigger = setActiveCollectionAction(null)
        const state = {
            collections: { activeCollection: exampleCollectionId },
            users: {
                keyPair: {
                    status: "FINISHED",
                    params: "",
                    data: { privateKey: examplePrivateKey, publicKey: examplePublicKey },
                },
            },
        } as AppState
        verifyEpic(trigger, collectionsEpics, state, { marbles: "---" })
    })

    it("should not trigger collection fetch and decrypt when collection changed if private key not decrypted", () => {
        const trigger = setActiveCollectionAction("second-collection")
        const state = {
            collections: { activeCollection: null },
            users: {
                keyPair: { status: "NOT_STARTED" },
            },
        } as AppState
        verifyEpic(trigger, collectionsEpics, state, { marbles: "---" })
    })

    it("should trigger remove preferred collection on local logout", () => {
        const trigger = localLogoutAction()
        const state = {} as AppState
        verifyEpic(trigger, collectionsEpics, state, {
            marbles: "-a",
            values: { a: removePreferredCollectionIdAction.started() },
        })
    })

    it("should trigger invitation accept if acceptance set to true", async () => {
        const acceptInvitationSpy = jest.spyOn(CollectionsApi, "acceptInvitation").mockResolvedValue(undefined)
        const trigger = changeInvitationAcceptanceAction.started({
            collectionId: exampleCollectionId,
            isAccepted: true,
        })
        const state = {} as AppState
        const expectedAction = changeInvitationAcceptanceAction.done({
            params: { collectionId: exampleCollectionId, isAccepted: true },
            result: undefined,
        })
        const result = await runAsyncEpic(trigger, collectionsEpics, state)
        expect(result).toStrictEqual(expectedAction)
        expect(acceptInvitationSpy).toHaveBeenCalledTimes(1)
        expect(acceptInvitationSpy).toHaveBeenCalledWith(exampleCollectionId)
    })

    it("should trigger invitation cancel if acceptance set to false", async () => {
        const cancelInvitationSpy = jest.spyOn(CollectionsApi, "cancelInvitationAcceptance").mockResolvedValue(undefined)
        const trigger = changeInvitationAcceptanceAction.started({
            collectionId: exampleCollectionId,
            isAccepted: false,
        })
        const state = {} as AppState
        const expectedAction = changeInvitationAcceptanceAction.done({
            params: { collectionId: exampleCollectionId, isAccepted: false },
            result: undefined,
        })
        const result = await runAsyncEpic(trigger, collectionsEpics, state)
        expect(result).toStrictEqual(expectedAction)
        expect(cancelInvitationSpy).toHaveBeenCalledTimes(1)
        expect(cancelInvitationSpy).toHaveBeenCalledWith(exampleCollectionId)
    })
})
