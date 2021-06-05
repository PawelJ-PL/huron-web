import { TFunction } from "i18next"
import { z, ZodType } from "zod"
import capitalize from "lodash/capitalize"

export function dateStringSchema(t: TFunction): ZodType<string> {
    return z
        .string()
        .refine((value) => !Number.isNaN(Date.parse(value)), { message: capitalize(t("common:not-valid-date")) })
}
