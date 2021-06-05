import { History, Path } from "history"

export function historyMock(params?: {
    replace?: (path: Path, state?: History.LocationState) => void
    push?: (path: Path, state?: History.LocationState) => void
}): History {
    const replace = params?.replace ?? (() => void 0)
    const push = params?.push ?? (() => void 0)

    return {
        replace,
        push,
    } as History
}
