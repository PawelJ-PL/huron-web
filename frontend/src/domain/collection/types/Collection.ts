import { z } from "zod"

export const CollectionSchema = z.object({
    id: z.string(),
    name: z.string(),
    encryptionKeyVersion: z.string(),
    owner: z.string(),
    isAccepted: z.boolean(),
})

export type Collection = z.infer<typeof CollectionSchema>
