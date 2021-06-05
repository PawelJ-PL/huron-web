/* eslint-disable @typescript-eslint/no-non-null-assertion */ //https://github.com/aikoven/typescript-fsa/issues/64
import { AsyncOperationResult } from "./AsyncOperationResult"
import { ActionCreator, AsyncActionCreators } from "typescript-fsa"
import { reducerWithInitialState } from "typescript-fsa-reducers"
import identity from "lodash/identity"

// eslint-disable-next-line @typescript-eslint/explicit-module-boundary-types
export const createReducer = <Params, Result, Error = unknown>(
    asyncActions: AsyncActionCreators<Params, Result, Error>,
    resetAction?: ActionCreator<void>,
    transformers?: {
        params: (p: Params) => Params
    }
) => {
    const paramsTransformer: (p: Params) => Params = transformers?.params ?? identity

    const baseReducer = reducerWithInitialState<AsyncOperationResult<Params, Result, Error>>({ status: "NOT_STARTED" })
        .case(asyncActions.started, (_, params) => ({
            status: "PENDING",
            params: paramsTransformer(params),
        }))
        .case(asyncActions.done, (_, action) => ({
            status: "FINISHED",
            data: action.result!, // https://github.com/aikoven/typescript-fsa/issues/64
            params: paramsTransformer(action.params!), // https://github.com/aikoven/typescript-fsa/issues/64
        }))
        .case(asyncActions.failed, (_, action) => ({
            status: "FAILED",
            error: action.error,
            params: paramsTransformer(action.params!), // https://github.com/aikoven/typescript-fsa/issues/64
        }))

    return resetAction === undefined ? baseReducer : baseReducer.case(resetAction, () => ({ status: "NOT_STARTED" }))
}
