import { ApiError } from "../../../application/api/ApiError"

export class CollectionNotEmpty extends ApiError {
    constructor(collectionId: string) {
        super(`Collection ${collectionId} is not empty`)
        this.name = new.target.name
        Object.setPrototypeOf(this, new.target.prototype)
    }
}

export class KeyVersionMismatch extends ApiError {
    constructor() {
        super("Collection encryption key version mismatch")
        this.name = new.target.name
        Object.setPrototypeOf(this, new.target.prototype)
    }
}

export class UserNotMemberOfCollection extends ApiError {
    constructor(userId: string, collectionId: string) {
        super(`User ${userId} is not a member of collection ${collectionId}`)
        this.name = new.target.name
        Object.setPrototypeOf(this, new.target.prototype)
    }
}
