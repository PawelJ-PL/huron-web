import { SymmetricDecryptionFailed } from "./errors"
import { reject } from "lodash"
import * as forge from "node-forge"
import { KeyPair } from "../types/KeyPair"

const PBKDF2_ITERATIONS = 2000
const KEY_SIZE = 32

const api = {
    deriveKey(password: string, salt: string): Promise<string> {
        return new Promise<string>((resolve, reject) => {
            forge.pkcs5.pbkdf2(password, salt, PBKDF2_ITERATIONS, KEY_SIZE, (err, key) => {
                if (err) {
                    reject(err)
                } else if (!key) {
                    reject(new Error("Key not derived"))
                } else {
                    resolve(forge.util.bytesToHex(key))
                }
            })
        })
    },

    generateKeyPair(length: number): Promise<KeyPair> {
        return new Promise<KeyPair>((resolve, reject) => {
            forge.pki.rsa.generateKeyPair({ bits: length }, (err, keypair) => {
                if (err) {
                    reject(err)
                } else if (!keypair) {
                    reject(new Error("Keypair not generated"))
                } else {
                    const pubPem = forge.pki.publicKeyToPem(keypair.publicKey).trim()
                    const privPem = forge.pki.privateKeyToPem(keypair.privateKey).trim()
                    resolve({ publicKey: pubPem, privateKey: privPem })
                }
            })
        })
    },

    async encrypt(input: string, keyHex: string): Promise<string> {
        try {
            const iv = await randomBytes(16)
            return await new Promise<string>((resolve, reject) => {
                try {
                    const key = forge.util.hexToBytes(keyHex)
                    const cipher = forge.cipher.createCipher("AES-CBC", key)
                    const inputUtf = forge.util.encodeUtf8(input)
                    const input64 = forge.util.encode64(inputUtf)
                    cipher.start({ iv })
                    cipher.update(forge.util.createBuffer(input64))
                    cipher.finish()
                    const encrypted = cipher.output.toHex()
                    resolve(`AES-CBC:${forge.util.bytesToHex(iv)}:${encrypted}`)
                } catch (err) {
                    reject(err)
                }
            })
        } catch (e) {
            return Promise.reject(e)
        }
    },

    decrypt(encryptedInput: string, keyHex: string): Promise<string> {
        try {
            const [algorithm, ivHex, ciphertext] = encryptedInput.split(":", 3)
            if (!algorithm || !ivHex || !ciphertext) {
                return Promise.reject(new Error("Malformed encrypted input"))
            }
            const iv = forge.util.hexToBytes(ivHex)
            const key = forge.util.hexToBytes(keyHex)
            if (isValidSymmetricCipher(algorithm)) {
                const decipher = forge.cipher.createDecipher(algorithm, key)
                decipher.start({ iv })
                decipher.update(forge.util.createBuffer(forge.util.hexToBytes(ciphertext)))
                const result = decipher.finish()
                if (!result) {
                    return Promise.reject(new SymmetricDecryptionFailed())
                }
                const result64 = forge.util.decode64(decipher.output.toString())
                const resultUtf = forge.util.decodeUtf8(result64)
                return Promise.resolve(resultUtf)
            } else {
                return Promise.reject(new Error(`${algorithm} is not valid cipher`))
            }
        } catch (e) {
            return Promise.reject(e)
        }
    },

    asymmetricEncrypt(input: string, publicKeyPem: string): Promise<string> {
        try {
            const pubKey = forge.pki.publicKeyFromPem(publicKeyPem)
            const inputUtf = forge.util.encodeUtf8(input)
            const input64 = forge.util.encode64(inputUtf)
            const result = pubKey.encrypt(input64)
            return Promise.resolve(forge.util.bytesToHex(result))
        } catch (e) {
            return Promise.reject(e)
        }
    },

    asymmetricDecrypt(encryptedInput: string, privateKeyPem: string): Promise<string> {
        try {
            const privKey = forge.pki.privateKeyFromPem(privateKeyPem)
            const encryptedBytes = forge.util.hexToBytes(encryptedInput)
            const decrypted = privKey.decrypt(encryptedBytes)
            const decrypted64 = forge.util.decode64(decrypted)
            const decryptedUtf = forge.util.decodeUtf8(decrypted64)
            return Promise.resolve(decryptedUtf)
        } catch (e) {
            return Promise.reject(e)
        }
    },

    randomBytes(length: number): Promise<string> {
        return randomBytes(length).then((bytes) => forge.util.bytesToHex(bytes))
    },
}

const randomBytes = (length: number) =>
    new Promise<forge.Bytes>((resolve) => {
        forge.random.getBytes(length, (err, result) => {
            if (err) {
                reject(err)
            } else if (!result) {
                reject("Unable to generate random bytes")
            } else {
                resolve(result)
            }
        })
    })

function isValidSymmetricCipher(cipherName: string): cipherName is forge.cipher.Algorithm {
    return ["AES-CBC"].includes(cipherName)
}

export default api
