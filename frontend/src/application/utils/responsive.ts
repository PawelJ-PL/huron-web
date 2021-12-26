export type BreakPoint = "base" | "sm" | "md" | "lg" | "xl" | "2xl"
export type ResponsiveConfig<T> = Partial<Record<BreakPoint, T>>
export type ResponsiveValue<T> = T | ResponsiveConfig<T>

export const createResponsiveConfig =
    <T>(tGuard: (value: ResponsiveValue<T>) => value is T) =>
    (value: ResponsiveValue<T> | undefined, defaultConfig: ResponsiveConfig<T>): ResponsiveConfig<T> => {
        if (value === undefined) {
            return defaultConfig
        } else if (tGuard(value)) {
            return { base: value }
        } else {
            return value
        }
    }

export const numberResponsiveConfig = createResponsiveConfig<number>(
    (value): value is number => typeof value === "number"
)
export const booleanResponsiveConfig = createResponsiveConfig<boolean>(
    (value): value is boolean => typeof value === "boolean"
)
