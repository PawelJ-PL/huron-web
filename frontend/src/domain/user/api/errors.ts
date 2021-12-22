import { ApiError } from "./../../../application/api/ApiError"
export class EmailAlreadyRegistered extends ApiError {
    constructor(email: string) {
        super(`User ${email} already registered`)
        Object.setPrototypeOf(this, EmailAlreadyRegistered.prototype)
        this.name = "EmailAlreadyRegistered"
    }
}

export class NicknameAlreadyRegistered extends ApiError {
    constructor(nickname: string) {
        super(`Nickname ${nickname} already registered`)
        this.name = new.target.name
        Object.setPrototypeOf(this, new.target.prototype)
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
