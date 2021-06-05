import { actionCreatorFactory } from "typescript-fsa"
import { createReducer } from "./AsyncActionReducer"
const actionCreator = actionCreatorFactory("REDUCER_TEST")
const asyncAction = actionCreator.async<{ foo: string; bar: number }, string, Error>("TRIGGER")
const resetAction = actionCreator("REDUCER_RESET")
const params = { foo: "BaZ", bar: 123 }

describe("Async operations reducer", () => {
    it("should set status PENDING", () => {
        const reducer = createReducer(asyncAction).build()
        const result = reducer(undefined, asyncAction.started(params))
        expect(result).toStrictEqual({ status: "PENDING", params })
    })

    it("should set status FINISHED", () => {
        const reducer = createReducer(asyncAction).build()
        const result = reducer(undefined, asyncAction.done({ params, result: "FooBar" }))
        expect(result).toStrictEqual({ status: "FINISHED", params, data: "FooBar" })
    })

    it("should set status FAILED", () => {
        const reducer = createReducer(asyncAction).build()
        const result = reducer(undefined, asyncAction.failed({ params, error: new Error("Some error") }))
        expect(result).toStrictEqual({ status: "FAILED", params, error: new Error("Some error") })
    })

    it("should reset status on reset action", () => {
        const reducer = createReducer(asyncAction, resetAction).build()
        const init = reducer(undefined, asyncAction.started(params))
        const result = reducer(init, resetAction())
        expect(result).toStrictEqual({ status: "NOT_STARTED" })
    })

    it("should transform params in PENDING action", () => {
        const reducer = createReducer(asyncAction, undefined, {
            params: ({ foo, bar }) => ({ foo: foo + "a", bar: bar + 1 }),
        }).build()
        const result = reducer(undefined, asyncAction.started(params))
        expect(result).toStrictEqual({ status: "PENDING", params: { foo: "BaZa", bar: 124 } })
    })

    it("should transform params in FINISHED action", () => {
        const reducer = createReducer(asyncAction, undefined, {
            params: ({ foo, bar }) => ({ foo: foo + "a", bar: bar + 1 }),
        }).build()
        const result = reducer(undefined, asyncAction.done({ params, result: "FooBar" }))
        expect(result).toStrictEqual({ status: "FINISHED", params: { foo: "BaZa", bar: 124 }, data: "FooBar" })
    })

    it("should transform params in FAILED action", () => {
        const reducer = createReducer(asyncAction, undefined, {
            params: ({ foo, bar }) => ({ foo: foo + "a", bar: bar + 1 }),
        }).build()
        const result = reducer(undefined, asyncAction.failed({ params, error: new Error("Some error") }))
        expect(result).toStrictEqual({
            status: "FAILED",
            params: { foo: "BaZa", bar: 124 },
            error: new Error("Some error"),
        })
    })
})
