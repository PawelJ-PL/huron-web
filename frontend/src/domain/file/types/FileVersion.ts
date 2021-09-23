import { z } from "zod"
import { dateStringSchema } from "../../../application/types/schemas"

export const FileVersionSchema = z.object({
    id: z.string(),
    collectionId: z.string(),
    versionId: z.string(),
    versionAuthor: z.string().nullish(),
    mimeType: z.string().nullish(),
    contentDigest: z.string().nullish(),
    encryptedSize: z.number(),
    updatedAt: dateStringSchema,
})

export type FileVersion = z.infer<typeof FileVersionSchema>
