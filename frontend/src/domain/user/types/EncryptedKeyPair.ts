import { z } from "zod"

export const EncryptedKeyPairSchema = z.object({
    algorithm: z.enum(["Rsa"]),
    publicKey: z.string(),
    encryptedPrivateKey: z.string().regex(/^AES-CBC:[0-9a-zA-Z]+:[0-9a-zA-Z]+$/),
})

export type EncryptedKeyPair = z.infer<typeof EncryptedKeyPairSchema>
