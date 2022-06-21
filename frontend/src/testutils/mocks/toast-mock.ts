import { UseToastOptions } from "@chakra-ui/react"

// eslint-disable-next-line @typescript-eslint/explicit-module-boundary-types
export const toastMock = (callMock?: jest.Mock) => {
    const mock = callMock ?? jest.fn()

    const fn = (options?: UseToastOptions) => {
        mock(options)
        return 1
    }
    const rest = {
        close: () => void 0,
        closeAll: () => void 0,
        update: () => void 0,
        isActive: () => false,
        promise: () => 1,
    }

    return Object.assign(fn, rest)
}
