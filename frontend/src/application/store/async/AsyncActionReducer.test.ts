import { actionCreatorFactory } from "typescript-fsa"
import { createMultipleValuesRecordReducer, createReducer } from "./AsyncActionReducer"
const actionCreator = actionCreatorFactory("REDUCER_TEST")
const asyncAction = actionCreator.async<{ foo: string; bar: number }, string, Error>("TRIGGER")
const resetAction = actionCreator("REDUCER_RESET")
const params = { foo: "BaZ", bar: 123 }

describe("Async operations reducer", () => {
    describe("create reducer", () => {
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

    describe("create multiple values record reducer", () => {
        const existingState = {
            x: { status: "PENDING" as const, params: { foo: "x", bar: 100 } },
            y: { status: "FAILED" as const, params: { foo: "y", bar: 101 }, error: new Error("Some error") },
            z: { status: "FINISHED" as const, params: { foo: "z", bar: 102 }, data: "OK" },
        }

        const reducer = createMultipleValuesRecordReducer(asyncAction, (p) => p.foo, resetAction)

        const actionParams = { foo: "a", bar: 1 }

        const actionStarted = asyncAction.started(actionParams)

        const actionFailed = asyncAction.failed({ params: actionParams, error: new Error("Some error") })

        const actionFinished = asyncAction.done({ params: actionParams, result: "Some data" })

        it("should add value to empty state on action started", () => {
            const result = reducer({}, actionStarted)
            expect(result).toStrictEqual({ a: { status: "PENDING", params: actionParams } })
        })

        it("should add value to non empty state on action started", () => {
            const result = reducer(existingState, actionStarted)
            expect(result).toStrictEqual({ ...existingState, a: { status: "PENDING", params: actionParams } })
        })

        it("should update failed status with pending", () => {
            const initValue = { a: { status: "FAILED" as const, params: actionParams, error: new Error("Some") } }
            const result = reducer(initValue, actionStarted)
            expect(result).toStrictEqual({ a: { status: "PENDING", params: actionParams } })
        })

        it("should not update finished status with pending", () => {
            const initValue = { a: { status: "FINISHED" as const, params: actionParams, data: "Something" } }
            const result = reducer(initValue, actionStarted)
            expect(result).toStrictEqual(initValue)
        })

        it("should add value to empty state on action failed", () => {
            const result = reducer({}, actionFailed)
            expect(result).toStrictEqual({
                a: { status: "FAILED", params: actionParams, error: new Error("Some error") },
            })
        })

        it("should add value to non empty state on action failed", () => {
            const result = reducer(existingState, actionFailed)
            expect(result).toStrictEqual({
                ...existingState,
                a: { status: "FAILED", params: actionParams, error: new Error("Some error") },
            })
        })

        it("should update pending status with failed", () => {
            const initValue = { a: { status: "PENDING" as const, params: actionParams } }
            const result = reducer(initValue, actionFailed)
            expect(result).toStrictEqual({
                a: { status: "FAILED", params: actionParams, error: new Error("Some error") },
            })
        })

        it("should update other failed status with failed", () => {
            const initValue = { a: { status: "FAILED" as const, params: actionParams, error: new Error("Old error") } }
            const result = reducer(initValue, actionFailed)
            expect(result).toStrictEqual({
                a: { status: "FAILED", params: actionParams, error: new Error("Some error") },
            })
        })

        it("should not update finished status with failed", () => {
            const initValue = { a: { status: "FINISHED" as const, params: actionParams, data: "OK" } }
            const result = reducer(initValue, actionFailed)
            expect(result).toStrictEqual(initValue)
        })

        it("should add value to empty state on action finished", () => {
            const result = reducer({}, actionFinished)
            expect(result).toStrictEqual({
                a: { status: "FINISHED", params: actionParams, data: "Some data" },
            })
        })

        it("should add value to non empty state on action finished", () => {
            const result = reducer(existingState, actionFinished)
            expect(result).toStrictEqual({
                ...existingState,
                a: { status: "FINISHED", params: actionParams, data: "Some data" },
            })
        })

        it("should update pending status with finished", () => {
            const initValue = { a: { status: "PENDING" as const, params: actionParams } }
            const result = reducer(initValue, actionFinished)
            expect(result).toStrictEqual({
                a: { status: "FINISHED", params: actionParams, data: "Some data" },
            })
        })

        it("should update failed status with finished", () => {
            const initValue = { a: { status: "FAILED" as const, params: actionParams, error: new Error("Some error") } }
            const result = reducer(initValue, actionFinished)
            expect(result).toStrictEqual({
                a: { status: "FINISHED", params: actionParams, data: "Some data" },
            })
        })

        it("should update other finished with finished", () => {
            const initValue = { a: { status: "FINISHED" as const, params: actionParams, data: "Old data" } }
            const result = reducer(initValue, actionFinished)
            expect(result).toStrictEqual({
                a: { status: "FINISHED", params: actionParams, data: "Some data" },
            })
        })

        it("should clean state on reset action", () => {
            const result = reducer(existingState, resetAction)
            expect(result).toStrictEqual({})
        })
    })
})
