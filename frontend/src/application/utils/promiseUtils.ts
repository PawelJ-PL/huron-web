// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function deferredPromise<T>(fn: (...args: any[]) => T, args: any[]): Promise<T> {
    return new Promise<T>((resolve, reject) => {
        setTimeout(() => {
            try {
                const result = fn(...args)
                resolve(result)
            } catch (err) {
                reject(err)
            }
        }, 0)
    })
}
