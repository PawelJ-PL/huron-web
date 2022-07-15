import { z } from "zod"

export const collectionPermissionSchema = z.enum([
    "ManageCollection",
    "CreateFile",
    "ModifyFile",
    "ReadFile",
    "ReadFileMetadata",
])

export type CollectionPermission = z.infer<typeof collectionPermissionSchema>
