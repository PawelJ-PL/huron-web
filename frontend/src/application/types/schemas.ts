import { z } from "zod"

export const dateStringSchema = z
    .string()
    .refine((value) => !Number.isNaN(Date.parse(value)), { message: "Not valid date" })
