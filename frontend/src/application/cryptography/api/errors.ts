import { ApiError } from "./../../api/ApiError"

export class SymmetricDecryptionFailed extends ApiError {
    constructor() {
        super("Unable to decrypt data")
        Object.setPrototypeOf(this, SymmetricDecryptionFailed.prototype)
        this.name = "SymmetricDecryptionFailed"
    }
}

export class AsymmetricDecryptionFailed extends ApiError {
    constructor() {
        super("Unable to decrypt data")
        Object.setPrototypeOf(this, AsymmetricDecryptionFailed.prototype)
        this.name = "AsymmetricDecryptionFailed"
    }
}
