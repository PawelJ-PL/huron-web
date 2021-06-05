import { ApiError } from "./../../../application/api/ApiError"
export class UserAlreadyRegistered extends ApiError {
    constructor(email: string) {
        super(`User ${email} already registered`)
        Object.setPrototypeOf(this, UserAlreadyRegistered.prototype)
        this.name = "UserAlreadyRegistered"
    }
}

export class PasswordsAreEqual extends ApiError {
    constructor() {
        super("Old and new passwords are equal")
        Object.setPrototypeOf(this, PasswordsAreEqual.prototype)
        this.name = "PasswordsAreEqual"
    }
}
export class InvalidCredentials extends ApiError {
    constructor() {
        super("Provided credentials are invalid")
        Object.setPrototypeOf(this, InvalidCredentials.prototype)
        this.name = "InvalidCredentials"
    }
}

export class InvalidEmail extends ApiError {
    constructor() {
        super("Provided email is invalid")
        Object.setPrototypeOf(this, InvalidEmail.prototype)
        this.name = "InvalidEmail"
    }
}
