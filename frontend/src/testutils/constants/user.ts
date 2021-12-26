import { UserPublicData } from "./../../domain/user/types/UserPublicData"
import { EncryptedKeyPair } from "../../domain/user/types/EncryptedKeyPair"

export const exampleUserEmail = "some@example.org"
export const exampleHashedEmail = "5976339c501ef0e78639242776736f5229e841dae232e16eee01d584ccdc01b1"
export const exampleUserPassword = "secret-password"
export const exampleUserNickname = "Adam"
export const exampleUserId = "c29a454b-0fc2-4660-b6a6-1122a95335e2"
export const exampleApiKey = {
    id: "3daa87ad-88b9-47d7-82c6-93fc386d0790",
    key: "example-api-key-value",
    enabled: true,
    description: "Test API key",
    validTo: undefined,
    createdAt: "2021-07-02T21:02:38.475Z",
    updatedAt: "2021-07-02T21:02:38.475Z",
}

export const examplePublicKey = "examplePublicKey"

export const examplePrivateKey = "examplePrivateKey"

export const exampleEncryptedPrivateKey = "exampleEncryptedPrivateKey"

export const exampleEncryptedKeypair: EncryptedKeyPair = {
    algorithm: "Rsa",
    publicKey: examplePublicKey,
    encryptedPrivateKey: exampleEncryptedPrivateKey,
}

export const exampleMasterKey = "12d424724067e66bbfc80f0df651695792a42307e9507b2725600016c8dbc337"

export const exampleContactAlias = "Frank"

export const exampleUserPublicData: UserPublicData = {
    userId: exampleUserId,
    nickName: exampleUserNickname,
    contactData: {
        alias: exampleContactAlias,
    },
}
