import { unicodeWordRegex } from "./../../../application/utils/regex"
import { z, ZodType } from "zod"
import { TFunction } from "i18next"
export function collectionNameSchema(t: TFunction): ZodType<string> {
    return z
        .string()
        .min(3, { message: t("common:field-min-length", { minChars: 3 }) })
        .max(30, { message: t("common:field-max-length", { maxChars: 30 }) })
        .regex(unicodeWordRegex, { message: t("common:field-word-regex") })
        .refine((val) => val.trim().length > 0, t("common:field-non-empty"))
}
