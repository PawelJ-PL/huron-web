import { Epic, StateObservable } from "redux-observable"
import { Action } from "typescript-fsa"
import { AppState } from "../application/store"
import { TestScheduler } from "rxjs/testing"
import { firstValueFrom, Observable, Subject, of } from "rxjs"
import { AnyAction } from "redux"

export const verifyEpic = (
    action: Action<unknown>,
    epic: Epic<AnyAction, AnyAction, AppState>,
    state: AppState,
    expected: { marbles: string; values?: unknown; error?: unknown }
): void => {
    const testScheduler = new TestScheduler((actual, expected) => expect(actual).toStrictEqual(expected))

    testScheduler.run(({ hot, expectObservable }) => {
        const input$ = hot("-a", { a: action })
        const state$ = new StateObservable(new Subject<AppState>(), state)
        const result = epic(input$, state$, {})

        expectObservable(result).toBe(expected.marbles, expected.values, expected.error)
    })
}

export function runAsyncEpic(
    action: Action<unknown>,
    epic: Epic<AnyAction, AnyAction, AppState>,
    state: AppState
): Promise<AnyAction> {
    const action$: Observable<AnyAction> = of(action)
    const state$ = new StateObservable(new Subject<AppState>(), state)
    const result = epic(action$, state$, {})
    return firstValueFrom(result)
}
