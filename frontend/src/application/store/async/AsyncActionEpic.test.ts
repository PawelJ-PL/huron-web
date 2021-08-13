import { verifyAsyncEpic } from "../../../testutils/epicsUtils"
import { actionCreatorFactory } from "typescript-fsa"
import { AppState } from ".."
import { createEpic } from "./AsyncActionEpic"
const asyncAction = actionCreatorFactory("EPIC_TEST").async<{ foo: string; bar: number }, string, Error>("TRIGGER")
const state = {} as AppState
const params = { foo: "BaZ", bar: 123 }

describe("Async epic creator", () => {
    it("should set state to finished", () => {
        const epic = createEpic(asyncAction, () => Promise.resolve("FooBar"))
        const trigger = asyncAction.started(params)
        return verifyAsyncEpic(trigger, epic, state, asyncAction.done({ params, result: "FooBar" }))
    })

    it("should set state to failed", () => {
        const epic = createEpic(asyncAction, () => Promise.reject(new Error("Some error")))
        const trigger = asyncAction.started(params)
        return verifyAsyncEpic(trigger, epic, state, asyncAction.failed({ params, error: new Error("Some error") }))
    })

    it("should use state to compute result", () => {
        const currentState: AppState = { users: { csrfToken: "QuX" } } as AppState
        const epic = createEpic(asyncAction, (params, s) => Promise.resolve(params.foo + s.users.csrfToken))
        const trigger = asyncAction.started(params)
        return verifyAsyncEpic(trigger, epic, currentState, asyncAction.done({ params, result: "BaZQuX" }))
    })
})
