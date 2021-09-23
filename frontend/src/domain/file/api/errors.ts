import { ApiError } from "../../../application/api/ApiError"

export class NotADirectory extends ApiError {
    constructor(objectId: string) {
        super(`Object ${objectId} is not a directory`)
        Object.setPrototypeOf(this, NotADirectory.prototype)
        this.name = "NotADirectory"
    }
}

export class FileNotFound extends ApiError {
    constructor(objectId: string, collectionId: string) {
        super(`Object ${objectId} from collection ${collectionId} not found`)
        Object.setPrototypeOf(this, FileNotFound.prototype)
        this.name = "FileNotFound"
    }
}

export class FileAlreadyExists extends ApiError {
    constructor(collectionId: string, parentId: string | null, name: string) {
        super(
            `File or directory ${name} already exists in directory ${parentId ?? "root"} in collection ${collectionId}`
        )
        Object.setPrototypeOf(this, FileAlreadyExists.prototype)
        this.name = "FileAlreadyExists"
    }
}

export class EncryptedFileTooLarge extends ApiError {
    readonly maxSize: number

    readonly realSize: number

    constructor(realSize: number, maxSize: number) {
        super(`Encrypted file is too large. Max size is ${maxSize} but file has ${realSize}`)
        Object.setPrototypeOf(this, EncryptedFileTooLarge.prototype)
        this.name = "EncryptedFileTooLarge"
        this.maxSize = maxSize
        this.realSize = realSize
    }
}

export class RecursivelyDelete extends ApiError {
    constructor(collectionId: string, fileId: string) {
        super(`Unable to delete directory ${fileId} from collection ${collectionId} because directory is not empty`)
        Object.setPrototypeOf(this, RecursivelyDelete.prototype)
        this.name = "RecursivelyDelete"
    }
}

export class FileContentNotChanged extends ApiError {
    constructor(collectionId: string, fileId: string) {
        super(`New version content of file ${fileId} from collection ${collectionId} is equal to the previous one`)
        Object.setPrototypeOf(this, FileContentNotChanged.prototype)
        this.name = "FileContentNotChanged"
    }
}
