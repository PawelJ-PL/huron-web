import { z } from "zod"

export const UserPublicDataSchema = z.object({
    userId: z.string(),
    nickName: z.string(),
    contactData: z
        .object({
            alias: z.string().nullish(),
        })
        .nullish(),
})

export type UserPublicData = z.infer<typeof UserPublicDataSchema>
