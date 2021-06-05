export type AsyncOperationResult<Params, Data, Error> =
    | { status: "NOT_STARTED" }
    | { status: "PENDING"; params: Params }
    | { status: "FINISHED"; params: Params; data: Data }
    | { status: "FAILED"; params: Params; error: Error }
