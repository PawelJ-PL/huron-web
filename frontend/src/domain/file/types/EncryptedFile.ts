import { z } from "zod"

const hexBytesPattern = /^(?:[a-fA-F0-9]{2})+$/

export const EncryptedFileSchema = z.object({
    content: z.object({
        algorithm: z.literal("AES-CBC"),
        iv: z.string().refine((value) => hexBytesPattern.test(value), { message: "Not valid IV" }),
        encryptionKeyVersion: z.string(),
        bytes: z.string().refine((value) => hexBytesPattern.test(value), { message: "Not valid file bytes" }),
    }),
    digest: z.string(),
    name: z.string(),
    mimeType: z.string().nullish(),
})

export type EncryptedFile = z.infer<typeof EncryptedFileSchema>
