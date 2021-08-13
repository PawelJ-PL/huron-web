import { Collection } from "./../../domain/collection/types/Collection"
import { EncryptionKey } from "./../../domain/collection/types/EncryptionKey"

export const exampleCollectionId = "1bffed24-f1a0-4108-839d-5741846a8606"
export const exampleEncryptionKeyVersion = "7c5c12fc-dfa4-40b3-bfec-3cb84716b609"
export const exampleEncryptedCollectionKey = "AES-CBC:aabbcc:1a2b3c"
export const exampleEncryptionKey: EncryptionKey = {
    collectionId: exampleCollectionId,
    key: exampleEncryptedCollectionKey,
    version: exampleEncryptionKeyVersion,
}
export const exampleCollectionName = "MyFirsCollection"
export const exampleCollection: Collection = {
    id: exampleCollectionId,
    name: exampleCollectionName,
    encryptionKeyVersion: exampleEncryptionKeyVersion,
}
