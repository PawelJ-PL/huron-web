import { EncryptionKeySchema } from "./../types/EncryptionKey"
import { errorResponseToData, validatedResponse } from "./../../../application/api/helpers"
import { client } from "../../../application/api/BaseClient"
import { EncryptionKey } from "../types/EncryptionKey"
import { Collection, CollectionSchema } from "../types/Collection"

type NewCollectionReq = { name: string; encryptedKey: string }

const api = {
    fetchAllEncryptionKeys(): Promise<EncryptionKey[]> {
        return client
            .get("collections/encryption-key")
            .then((resp) => validatedResponse(resp, EncryptionKeySchema.array()))
    },
    fetchEncryptionKey(collectionId: string): Promise<EncryptionKey | null> {
        return client
            .get(`collections/${collectionId}/encryption-key`)
            .then((resp) => validatedResponse(resp, EncryptionKeySchema))
            .catch((error) => errorResponseToData(error, null, 404, 403))
    },
    listCollections(onlyAccepted: boolean): Promise<Collection[]> {
        return client
            .get("collections", { searchParams: { onlyAccepted } })
            .then((resp) => validatedResponse(resp, CollectionSchema.array()))
    },
    fetchCollection(collectionId: string): Promise<Collection | null> {
        return client
            .get(`collections/${collectionId}`)
            .then((resp) => validatedResponse(resp, CollectionSchema))
            .catch((error) => errorResponseToData(error, null, 400, 403, 404))
    },
    createCollection(data: NewCollectionReq): Promise<Collection> {
        return client.post("collections", { json: data }).then((resp) => validatedResponse(resp, CollectionSchema))
    },
    acceptInvitation(collectionId: string): Promise<void> {
        return client.put(`collections/${collectionId}/members/me/approval`).then(() => undefined)
    },
    cancelInvitationAcceptance(collectionId: string): Promise<void> {
        return client.delete(`collections/${collectionId}/members/me/approval`).then(() => undefined)
    },
}

export default api
