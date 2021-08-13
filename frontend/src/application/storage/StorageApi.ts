export type StorageType = "Local" | "Session"

const api = {
    getItem: (key: string, storage?: StorageType): Promise<string | null> => {
        if (storage === undefined) {
            return Promise.resolve(sessionStorage.getItem(key) ?? localStorage.getItem(key))
        } else {
            return withStorage(storage, (s) => s.getItem(key))
        }
    },
    setItem: (key: string, value: string, storage: StorageType): Promise<void> => {
        return withStorage(storage, (s) => s.setItem(key, value))
    },
    removeItem: (key: string, storage?: StorageType): Promise<void> => {
        if (storage === undefined) {
            return Promise.resolve(sessionStorage.removeItem(key)).then(() => localStorage.removeItem(key))
        } else {
            return withStorage(storage, (s) => s.removeItem(key))
        }
    },
}

const withStorage = <A>(storageType: StorageType, action: (storage: Storage) => A): Promise<A> => {
    switch (storageType) {
        case "Local":
            return Promise.resolve(action(localStorage))
        case "Session":
            return Promise.resolve(action(sessionStorage))
    }
}

export default api
