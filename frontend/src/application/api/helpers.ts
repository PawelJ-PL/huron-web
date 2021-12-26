import { ApiError } from "./ApiError"
import { HTTPError } from "ky"
import { ZodSchema, ZodType } from "zod/lib/types"
import { z } from "zod"
import { Pagination } from "./Pagination"

export const errorResponseToData: <T>(e: Error, d: T, ...c: number[]) => Promise<T> = <T>(
    error: Error,
    data: T,
    ...expectedCodes: number[]
) => {
    if (error instanceof HTTPError && expectedCodes.includes(error.response.status)) {
        return Promise.resolve(data)
    } else {
        return Promise.reject(error)
    }
}

export const errorResponseToError: <E extends ApiError>(e1: Error, e2: E, ...c: number[]) => Promise<never> = <
    E extends ApiError
>(
    responseError: Error,
    resultError: E,
    ...expectedCodes: number[]
) => {
    if (responseError instanceof HTTPError && expectedCodes.includes(responseError.response.status)) {
        return Promise.reject(resultError)
    } else {
        return Promise.reject(responseError)
    }
}

export async function errorResponseReasonToError<E extends ApiError>(
    responseError: Error,
    resultError: E,
    expectedCode: number,
    expectedReason: string
): Promise<never> {
    if (responseError instanceof HTTPError && responseError.response.status === expectedCode) {
        try {
            const responseCopy = responseError.response.clone()
            const body = await responseCopy.json()
            if (body["reason"] === expectedReason) {
                return Promise.reject(resultError)
            } else {
                return Promise.reject(responseError)
            }
        } catch (err) {
            return Promise.reject(responseError)
        }
    } else {
        return Promise.reject(responseError)
    }
}

export const validatedResponse: <T>(resp: Response, schema: ZodSchema<T>) => Promise<T> = <T>(
    resp: Response,
    schema: ZodType<T>
) => resp.json().then((d) => schema.parse(d))

const numericHeader = (resp: Response, header: string) =>
    z
        .string()
        .refine((str) => !Number.isNaN(Number(str)), { message: "Not valid number" })
        .transform((str) => Number(str))
        .parse(resp.headers.get(header))

const optionalNumericHeader = (resp: Response, header: string) =>
    z
        .string()
        .nullable()
        .refine((optStr) => optStr === null || !Number.isNaN(Number(optStr)), { message: "Not valid number" })
        .transform((optStr) => (optStr === null ? null : Number(optStr)))
        .nullable()
        .parse(resp.headers.get(header))

export const validatePagedResponse = async <T>(resp: Response, schema: ZodType<T>): Promise<Pagination<T>> => {
    const json = await resp.json()
    const data = schema.parse(json)
    const page = numericHeader(resp, "X-Page")
    const elementsPerPage = numericHeader(resp, "X-Elements-Per-Page")
    const totalPages = numericHeader(resp, "X-Total-Pages")
    const prevPage = optionalNumericHeader(resp, "X-Prev-Page")
    const nextPage = optionalNumericHeader(resp, "X-Next-Page")
    return {
        result: data,
        page,
        elementsPerPage,
        totalPages,
        prevPage: prevPage ?? undefined,
        nextPage: nextPage ?? undefined,
    }
}

export function validateNonEmptyData<Data>(data: Data): boolean {
    return Object.values(data).some((value) => value !== undefined && value !== null)
}
