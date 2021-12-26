import { z } from "zod"

export const UserContactSchema = z.object({
    userId: z.string(),
    nickName: z.string(),
    alias: z.string().nullish(),
})

export type UserContact = z.infer<typeof UserContactSchema>
