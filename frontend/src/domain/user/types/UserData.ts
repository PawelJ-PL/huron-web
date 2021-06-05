import { z } from "zod"

export const UserDataSchema = z.object({
    id: z.string(),
    nickName: z.string(),
    language: z.string(),
})

export type UserData = z.infer<typeof UserDataSchema>
