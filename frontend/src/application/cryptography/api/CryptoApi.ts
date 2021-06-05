import * as forge from "node-forge"

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
}

export default api
