import { CollectionPermission, collectionPermissionSchema } from "./../types/CollectionPermission"
import { EncryptionKeySchema } from "./../types/EncryptionKey"
import { errorResponseReasonToError, errorResponseToData, validatedResponse } from "./../../../application/api/helpers"
import { client } from "../../../application/api/BaseClient"
import { EncryptionKey } from "../types/EncryptionKey"
import { Collection, CollectionSchema } from "../types/Collection"
import { z } from "zod"
import { CollectionNotEmpty, KeyVersionMismatch, UserNotMemberOfCollection } from "./errors"

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
    getCollectionMembers(collectionId: string): Promise<Record<string, CollectionPermission[]> | null> {
        return client
            .get(`collections/${collectionId}/members`)
            .then((resp) => validatedResponse(resp, z.record(collectionPermissionSchema.array())))
            .catch((error) => errorResponseToData(error, null, 400, 403, 404))
    },
    getMemberPermissions(collectionId: string, memberId: string): Promise<CollectionPermission[]> {
        return client
            .get(`collections/${collectionId}/members/${memberId}/permission`)
            .then((resp) => validatedResponse(resp, collectionPermissionSchema.array()))
    },
    deleteCollection(collectionId: string): Promise<void> {
        return client
            .delete(`collections/${collectionId}`)
            .then(() => undefined)
            .catch((err) =>
                errorResponseReasonToError(err, new CollectionNotEmpty(collectionId), 412, "CollectionNotEmpty")
            )
    },
    addMember(data: {
        collectionId: string
        userId: string
        collectionKeyVersion: string
        encryptedCollectionKey: string
        permissions: CollectionPermission[]
    }): Promise<void> {
        const { collectionId, userId, ...body } = data
        return client
            .put(`collections/${collectionId}/members/${userId}`, { json: body })
            .then(() => undefined)
            .catch((err) => errorResponseReasonToError(err, new KeyVersionMismatch(), 412, "KeyVersionMismatch"))
    },
    deleteMember(collectionId: string, memberId: string): Promise<void> {
        return client
            .delete(`collections/${collectionId}/members/${memberId}`)
            .then(() => undefined)
            .catch((e) =>
                errorResponseReasonToError(
                    e,
                    new UserNotMemberOfCollection(memberId, collectionId),
                    412,
                    "UserIsNotAMember"
                )
            )
    },
    setMemberPermissions(collectionId: string, memberId: string, permissions: CollectionPermission[]): Promise<void> {
        return client
            .put(`collections/${collectionId}/members/${memberId}/permission`, { json: permissions })
            .then(() => undefined)
            .catch((e) =>
                errorResponseReasonToError(
                    e,
                    new UserNotMemberOfCollection(memberId, collectionId),
                    412,
                    "UserIsNotAMember"
                )
            )
    },
}

export default api
