import { TFunction } from "i18next"
import { z, ZodType } from "zod"

export function filenameSchema(t: TFunction): ZodType<string> {
    return z
        .string()
        .min(1, { message: t("common:field-min-length", { minChars: 1 }) })
        .max(255, { message: t("common:field-max-length", { maxChars: 255 }) })
        .regex(/^[^/]+$/, { message: t("file-view:directory-content-list:invalid-name") })
}
