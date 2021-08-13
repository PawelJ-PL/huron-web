import { z } from "zod"

export const EncryptionKeySchema = z.object({
    collectionId: z.string(),
    key: z.string(),
    version: z.string(),
})

export type EncryptionKey = z.infer<typeof EncryptionKeySchema>
