import { z } from "zod"

const dateStringSchema = z.string().refine((value) => !Number.isNaN(Date.parse(value)), { message: "Not valid date" })

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
