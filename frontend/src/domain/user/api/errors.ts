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

export class TooManyUsersToFetch extends ApiError {
    constructor() {
        super("Too many users to fetch in single call")
        this.name = new.target.name
        Object.setPrototypeOf(this, new.target.prototype)
    }
}

export class ContactWithAliasAlreadyExists extends ApiError {
    readonly alias: string

    constructor(alias: string) {
        super(`Contact with alias ${alias} already exists`)
        this.name = new.target.name
        Object.setPrototypeOf(this, new.target.prototype)
        this.alias = alias
    }
}

export class UserAlreadyInContacts extends ApiError {
    constructor(userId: string) {
        super(`Contact ${userId} already exists in contacts`)
        this.name = new.target.name
        Object.setPrototypeOf(this, new.target.prototype)
    }
}

export class AddSelfToContacts extends ApiError {
    constructor() {
        super("Impossibel to add self to contacts")
        this.name = new.target.name
        Object.setPrototypeOf(this, new.target.prototype)
    }
}

export class NickNameQueryTooShort extends ApiError {
    constructor(query: string) {
        super(`Nickname query ${query} is too short`)
        this.name = new.target.name
        Object.setPrototypeOf(this, new.target.prototype)
    }
}
