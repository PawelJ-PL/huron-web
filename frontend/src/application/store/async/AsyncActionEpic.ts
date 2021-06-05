import { Observable, ObservableInput, from } from "rxjs"
import { catchError } from "rxjs/operators"
import { filter } from "rxjs/operators"
import { map } from "rxjs/operators"
import { mergeMap } from "rxjs/operators"
import { AnyAction, AsyncActionCreators } from "typescript-fsa"

export const createEpic = <Params, Result, Error>(
    asyncActions: AsyncActionCreators<Params, Result, Error>,
    requestCreator: (params: Params) => ObservableInput<Result>
) => {
    // eslint-disable-next-line @typescript-eslint/explicit-module-boundary-types
    return (actions$: Observable<AnyAction>) =>
        actions$.pipe(
            filter(asyncActions.started.match),
            mergeMap((action) =>
                from(requestCreator(action.payload)).pipe(
                    map((resp) => asyncActions.done({ result: resp, params: action.payload })),
                    catchError((err: Error) => [asyncActions.failed({ params: action.payload, error: err })])
                )
            )
        )
}
