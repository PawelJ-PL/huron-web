export class ApiError extends Error {
    constructor(m: string) {
        super(m)
        Object.setPrototypeOf(this, ApiError.prototype)
    }
}

export class Unauthorized extends ApiError {
    readonly needLogout: boolean

    constructor(needLogout?: boolean) {
        super("API returns response Unauthorized")
        Object.setPrototypeOf(this, Unauthorized.prototype)
        this.name = "Unauthorized"
        this.needLogout = needLogout ?? false
    }
}

export class NotLoggedIn extends ApiError {
    constructor() {
        super("User not logged in")
        Object.setPrototypeOf(this, NotLoggedIn.prototype)
        this.name = "NotLoggedIn"
    }
}

export class NoUpdatesProvides extends ApiError {
    constructor() {
        super("No updates provided")
        Object.setPrototypeOf(this, NoUpdatesProvides.prototype)
        this.name = "NoUpdatesProvided"
    }
}
