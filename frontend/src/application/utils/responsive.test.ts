import { createResponsiveConfig, ResponsiveConfig } from "./responsive"

describe("responsive config", () => {
    const stringConfigCreator = createResponsiveConfig<string>((value): value is string => typeof value === "string")
    const defaultConfig = { base: "bar" }

    it("should create config based on single value", () => {
        const result = stringConfigCreator("foo", defaultConfig)
        expect(result).toStrictEqual({ base: "foo" })
    })

    it("should return provided config", () => {
        const input: ResponsiveConfig<string> = { base: "foo", lg: "bar" }
        const result = stringConfigCreator(input, defaultConfig)
        expect(result).toStrictEqual(input)
    })

    it("should return default config if no value provided", () => {
        const result = stringConfigCreator(undefined, defaultConfig)
        expect(result).toStrictEqual(defaultConfig)
    })
})
