import { ApiError } from "./ApiError"
import { HTTPError } from "ky"
import { ZodSchema, ZodType } from "zod/lib/types"
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

export function validateNonEmptyData<Data>(data: Data): boolean {
    return Object.values(data).some((value) => value !== undefined && value !== null)
}
