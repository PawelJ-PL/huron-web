import { z } from "zod"

export const publicKeyDataSchema = z.object({
    algorithm: z.enum(["Rsa"]),
    publicKey: z.string(),
})

export type PublicKeyData = z.infer<typeof publicKeyDataSchema>

export const EncryptedKeyPairSchema = publicKeyDataSchema.merge(
    z.object({
        encryptedPrivateKey: z.string().regex(/^AES-CBC:[0-9a-zA-Z]+:[0-9a-zA-Z]+$/),
    })
)

export type EncryptedKeyPair = z.infer<typeof EncryptedKeyPairSchema>
