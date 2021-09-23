import { z } from "zod"
import { dateStringSchema } from "../../../application/types/schemas"

export const DirectoryMetadataSchema = z.object({
    id: z.string(),
    collectionId: z.string(),
    parent: z.string().nullish(),
    name: z.string(),
    "@type": z.literal("DirectoryData"),
})

export type DirectoryMetadata = z.infer<typeof DirectoryMetadataSchema>

export const FileMetadataSchema = z.object({
    id: z.string(),
    collectionId: z.string(),
    parent: z.string().nullish(),
    name: z.string(),
    description: z.string().nullish(),
    versionId: z.string(),
    versionAuthor: z.string().nullish(),
    mimeType: z.string().nullish(),
    contentDigest: z.string(),
    encryptedSize: z.number().positive(),
    updatedAt: dateStringSchema,
    "@type": z.literal("FileData"),
})

export type FileMetadata = z.infer<typeof FileMetadataSchema>

export const FilesystemUnitMetadataSchema = z.union([DirectoryMetadataSchema, FileMetadataSchema])

export type FilesystemUnitMetadata = z.infer<typeof FilesystemUnitMetadataSchema>
