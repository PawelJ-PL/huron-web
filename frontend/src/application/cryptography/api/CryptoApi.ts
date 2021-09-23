import { SymmetricDecryptionFailed } from "./errors"
import { reject } from "lodash"
import * as forge from "node-forge"
import { KeyPair } from "../types/KeyPair"
import { deferredPromise } from "../../utils/promiseUtils"
import isString from "lodash/isString"

const PBKDF2_ITERATIONS = 2000
const KEY_SIZE = 32
const DEFAULT_CHUNK_SIZE = 1024 * 64

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

    encryptString(input: string, keyHex: string, encodeUtf8: boolean, chunkSize?: number): Promise<string> {
        return encryptData(input, keyHex, encodeUtf8, chunkSize)
    },

    async decryptToString(
        encryptedInput: string,
        keyHex: string,
        decodeUtf8: boolean,
        chunkSize?: number
    ): Promise<string> {
        const output = await decryptData(encryptedInput, keyHex, chunkSize)
        const resultString = output.toString()
        const safeResult = await (decodeUtf8
            ? deferredPromise(forge.util.decode64, [resultString])
            : Promise.resolve(resultString))
        const decodedResult = await (decodeUtf8
            ? deferredPromise(forge.util.decodeUtf8, [safeResult])
            : Promise.resolve(safeResult))
        return decodedResult
    },

    encryptBinary(input: ArrayBuffer, keyHex: string, chunkSize?: number): Promise<string> {
        if (!forge.util.isArrayBuffer(input)) {
            return Promise.reject(new Error("Encryption input is not ArrayBuffer"))
        }
        return encryptData(input, keyHex, false, chunkSize)
    },

    async decryptBinary(encryptedInput: string, keyHex: string, chunkSize?: number): Promise<ArrayBuffer> {
        const output = await decryptData(encryptedInput, keyHex, chunkSize)
        const bytes = output.getBytes(output.length())
        return await deferredPromise(forge.util.binary.raw.decode, [bytes])
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

    async digest(input: string, chunkSize?: number): Promise<string> {
        const finalChunkSize = chunkSize ?? DEFAULT_CHUNK_SIZE
        if (finalChunkSize < 1) {
            return Promise.reject(new Error("Digest chunk size must be greater than 0"))
        }
        const utfInput = await deferredPromise(forge.util.encodeUtf8, [input])
        let index = 0
        const messageDigest = forge.md.sha256.create()
        do {
            const part = utfInput.substr(index, finalChunkSize)

            await new Promise((resolve, reject) => {
                setTimeout(() => {
                    try {
                        const result = messageDigest.update(part)
                        resolve(result)
                    } catch (err) {
                        reject(err)
                    }
                }, 0)
            })

            index += finalChunkSize
        } while (index < utfInput.length)
        return messageDigest.digest().toHex()
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

async function encryptData(
    input: string | ArrayBuffer,
    keyHex: string,
    encodeUtf8: boolean,
    chunkSize?: number
): Promise<string> {
    const finalChunkSize = chunkSize ?? DEFAULT_CHUNK_SIZE
    if (finalChunkSize < 1) {
        return Promise.reject(new Error("Encryption chunk size must be greater than 0"))
    }
    const iv = await randomBytes(16)
    const key = forge.util.hexToBytes(keyHex)
    const safeInput = await (isString(input) ? toSafeString(input, encodeUtf8) : Promise.resolve(input))
    const cipher = forge.cipher.createCipher("AES-CBC", key)
    cipher.start({ iv })
    let index = 0
    const inputSize = isString(safeInput) ? safeInput.length : safeInput.byteLength
    do {
        const buffer = await deferredPromise(forge.util.createBuffer, [inputSlice(safeInput, index, finalChunkSize)])
        await new Promise((resolve, reject) => {
            setTimeout(() => {
                try {
                    const result = cipher.update(buffer)
                    resolve(result)
                } catch (err) {
                    reject(err)
                }
            }, 0)
        })
        index += finalChunkSize
    } while (index < inputSize)
    cipher.finish()
    const encrypted = cipher.output.toHex()
    return `AES-CBC:${forge.util.bytesToHex(iv)}:${encrypted}`
}

async function toSafeString(input: string, encodeUtf8: boolean) {
    const encodedInput = await (encodeUtf8 ? deferredPromise(forge.util.encodeUtf8, [input]) : Promise.resolve(input))
    return await (encodeUtf8 ? deferredPromise(forge.util.encode64, [encodedInput]) : encodedInput)
}

function inputSlice(input: string | ArrayBuffer, start: number, size: number): string | ArrayBuffer {
    if (isString(input)) {
        return input.substr(start, size)
    } else {
        return input.slice(start, start + size)
    }
}

async function decryptData(
    encryptedInput: string,
    keyHex: string,
    chunkSize?: number
): Promise<forge.util.ByteStringBuffer> {
    const finalChunkSize = chunkSize ?? DEFAULT_CHUNK_SIZE
    if (finalChunkSize < 1) {
        return Promise.reject(new Error("Decryption chunk size must be greater than 0"))
    }
    try {
        const [algorithm, ivHex, ciphertext] = encryptedInput.split(":", 3)
        if (!algorithm || !ivHex || !ciphertext) {
            return Promise.reject(new Error("Malformed encrypted input"))
        }
        const iv = forge.util.hexToBytes(ivHex)
        const key = forge.util.hexToBytes(keyHex)
        let index = 0
        if (isValidSymmetricCipher(algorithm)) {
            const decipher = forge.cipher.createDecipher(algorithm, key)
            decipher.start({ iv })
            do {
                const part = ciphertext.substr(index, finalChunkSize * 2)
                const bytes = await deferredPromise(forge.util.hexToBytes, [part])
                const buffer = await deferredPromise(forge.util.createBuffer, [bytes])
                await new Promise((resolve, reject) => {
                    setTimeout(() => {
                        try {
                            const result = decipher.update(buffer)
                            resolve(result)
                        } catch (err) {
                            reject(err)
                        }
                    }, 0)
                })
                index += finalChunkSize * 2
            } while (index < ciphertext.length)
            const result = decipher.finish()
            if (!result) {
                return Promise.reject(new SymmetricDecryptionFailed())
            }
            return Promise.resolve(decipher.output)
        } else {
            return Promise.reject(new Error(`${algorithm} is not valid cipher`))
        }
    } catch (e) {
        return Promise.reject(e)
    }
}

export default api
