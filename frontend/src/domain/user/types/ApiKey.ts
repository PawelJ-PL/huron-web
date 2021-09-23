import { z } from "zod"
import { dateStringSchema } from "../../../application/types/schemas"

export const ApiKeyDescriptionSchema = z.object({
    id: z.string(),
    key: z.string(),
    enabled: z.boolean(),
    description: z.string(),
    validTo: dateStringSchema.nullish(),
    createdAt: dateStringSchema,
    updatedAt: dateStringSchema,
})

export type ApiKeyDescription = z.infer<typeof ApiKeyDescriptionSchema>
