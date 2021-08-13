import { onlyLettersRegex, unicodeWordRegex } from "./../../../application/utils/regex"
import { TFunction } from "i18next"
import { ZodType, z } from "zod"

export function nicknameSchema(t: TFunction): ZodType<string> {
    return z
        .string()
        .min(3, { message: t("common:field-min-length", { minChars: 3 }) })
        .max(30, { message: t("common:field-max-length", { maxChars: 30 }) })
        .regex(onlyLettersRegex, t("common:field-letters-only"))
}

export function passwordSchema(t: TFunction): ZodType<string> {
    return z
        .string()
        .min(15, { message: t("signup-page:password-min-length", { minChars: 15 }) })
        .refine((val) => val.trim().length > 0, t("common:field-non-empty"))
}

export const languageSchema = z.enum(["En", "Pl"])

export function isLanguage(value: string): value is z.infer<typeof languageSchema> {
    return Array.from<string>(languageSchema.options).includes(value)
}

export function apiKeyDescriptionSchema(t: TFunction): ZodType<string> {
    return z
        .string()
        .min(1, { message: t("common:field-min-length", { minChars: 3 }) })
        .max(80, { message: t("common:field-max-length", { maxChars: 80 }) })
        .regex(unicodeWordRegex, t("profile-page:api-key-description-pattern-not-match"))
        .refine((val) => val.trim().length > 0, t("common:field-non-empty"))
}
