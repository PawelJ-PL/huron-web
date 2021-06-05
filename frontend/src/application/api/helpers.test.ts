import {
    errorResponseReasonToError,
    errorResponseToData,
    errorResponseToError,
    validatedResponse,
    validateNonEmptyData,
} from "./helpers"
import { HTTPError, NormalizedOptions } from "ky"
import { z, ZodError } from "zod"

describe("HTTP API helpers", () => {
    describe("error response to data", () => {
        it("should turn error with matching status code into value", async () => {
            const response = { status: 501 } as Response
            const request = {} as Request
            const opts = {} as NormalizedOptions
            const error = new HTTPError(response, request, opts)
            const value = "FooBar"
            const result = errorResponseToData(error, value, 500, 501, 502)
            await expect(result).resolves.toBe(value)
        })

        it("should return original error if not HTTPError type", async () => {
            const error = new Error("Some error")
            const value = "FooBar"
            const result = errorResponseToData(error, value, 500, 501, 502)
            await expect(result).rejects.toBe(error)
        })

        it("should return original error if status code does not match", async () => {
            const response = { status: 401 } as Response
            const request = {} as Request
            const opts = {} as NormalizedOptions
            const error = new HTTPError(response, request, opts)
            const value = "FooBar"
            const result = errorResponseToData(error, value, 500, 501, 502)
            await expect(result).rejects.toBe(error)
        })
    })

    describe("error response to error", () => {
        it("should turn error with matching status code into expected error", async () => {
            const response = { status: 501 } as Response
            const request = {} as Request
            const opts = {} as NormalizedOptions
            const error = new HTTPError(response, request, opts)
            const desiredError = new Error("Expected error")
            const result = errorResponseToError(error, desiredError, 500, 501, 502)
            await expect(result).rejects.toBe(desiredError)
        })

        it("should return original error if not HTTPError type", async () => {
            const error = new Error("Some error")
            const desiredError = new Error("Expected error")
            const result = errorResponseToError(error, desiredError, 500, 501, 502)
            await expect(result).rejects.toBe(error)
        })

        it("should return original error if status code does not match", async () => {
            const response = { status: 401 } as Response
            const request = {} as Request
            const opts = {} as NormalizedOptions
            const error = new HTTPError(response, request, opts)
            const desiredError = new Error("Expected error")
            const result = errorResponseToError(error, desiredError, 500, 501, 502)
            await expect(result).rejects.toBe(error)
        })
    })

    describe("error response reasone to error", () => {
        it("should turn error with matching status code and reason into expected error", async () => {
            const baseResponse = { status: 412, json: () => Promise.resolve({ reason: "SomeReason" }) } as Response
            const clonableResponse = { ...baseResponse, clone: () => baseResponse }
            const request = {} as Request
            const opts = {} as NormalizedOptions
            const error = new HTTPError(clonableResponse, request, opts)
            const desiredError = new Error("Expected error")
            const result = errorResponseReasonToError(error, desiredError, 412, "SomeReason")
            await expect(result).rejects.toBe(desiredError)
        })

        it("should return original error if not HTTPError type", async () => {
            const error = new Error("Some error")
            const desiredError = new Error("Expected error")
            const result = errorResponseReasonToError(error, desiredError, 412, "SomeReason")
            await expect(result).rejects.toBe(error)
        })

        it("should return original error if status code does not match", async () => {
            const baseResponse = { status: 500, json: () => Promise.resolve({ reason: "SomeReason" }) } as Response
            const clonableResponse = { ...baseResponse, clone: () => baseResponse }
            const request = {} as Request
            const opts = {} as NormalizedOptions
            const error = new HTTPError(clonableResponse, request, opts)
            const desiredError = new Error("Expected error")
            const result = errorResponseReasonToError(error, desiredError, 412, "SomeReason")
            await expect(result).rejects.toBe(error)
        })

        it("should return original error if reading reason failed", async () => {
            const baseResponse = { status: 412, json: () => Promise.reject(new Error("JSON error")) } as Response
            const clonableResponse = { ...baseResponse, clone: () => baseResponse }
            const request = {} as Request
            const opts = {} as NormalizedOptions
            const error = new HTTPError(clonableResponse, request, opts)
            const desiredError = new Error("Expected error")
            const result = errorResponseReasonToError(error, desiredError, 412, "SomeReason")
            await expect(result).rejects.toBe(error)
        })

        it("should return original error if reason does not match", async () => {
            const baseResponse = { status: 412, json: () => Promise.resolve({ reason: "OtherReason" }) } as Response
            const clonableResponse = { ...baseResponse, clone: () => baseResponse }
            const request = {} as Request
            const opts = {} as NormalizedOptions
            const error = new HTTPError(clonableResponse, request, opts)
            const desiredError = new Error("Expected error")
            const result = errorResponseReasonToError(error, desiredError, 412, "SomeReason")
            await expect(result).rejects.toBe(error)
        })

        it("should return original error if reason is missing", async () => {
            const baseResponse = { status: 412, json: () => Promise.resolve({ foo: "bar" }) } as Response
            const clonableResponse = { ...baseResponse, clone: () => baseResponse }
            const request = {} as Request
            const opts = {} as NormalizedOptions
            const error = new HTTPError(clonableResponse, request, opts)
            const desiredError = new Error("Expected error")
            const result = errorResponseReasonToError(error, desiredError, 412, "SomeReason")
            await expect(result).rejects.toBe(error)
        })
    })

    describe("validate response", () => {
        const schema = z.object({
            foo: z.string(),
            bar: z.number(),
        })

        it("should validate correct response", async () => {
            const response = { json: () => Promise.resolve({ foo: "x", bar: 123 }) } as Response
            const result = validatedResponse(response, schema)
            await expect(result).resolves.toStrictEqual({ foo: "x", bar: 123 })
        })

        it("should strip redundant data", async () => {
            const response = { json: () => Promise.resolve({ foo: "x", bar: 123, baz: true }) } as Response
            const result = validatedResponse(response, schema)
            await expect(result).resolves.toStrictEqual({ foo: "x", bar: 123 })
        })

        it("should fail on invalid data", async () => {
            const response = { json: () => Promise.resolve({ x: "y" }) } as Response
            const result = validatedResponse(response, schema)
            await expect(result).rejects.toEqual(
                new ZodError([
                    {
                        code: "invalid_type",
                        expected: "string",
                        received: "undefined",
                        path: ["foo"],
                        message: "Required",
                    },
                    {
                        code: "invalid_type",
                        expected: "number",
                        received: "undefined",
                        path: ["bar"],
                        message: "Required",
                    },
                ])
            )
        })
    })

    describe("validate non empty data", () => {
        it("should validate data containing at least one property", () => {
            const data = { foo: "bar" }
            const result = validateNonEmptyData(data)
            expect(result).toBe(true)
        })

        it("should not validate empty object", () => {
            const data = {}
            const result = validateNonEmptyData(data)
            expect(result).toBe(false)
        })

        it("should not validate object with only null or undefined values", () => {
            const data = { foo: null, bar: undefined }
            const result = validateNonEmptyData(data)
            expect(result).toBe(false)
        })
    })
})
