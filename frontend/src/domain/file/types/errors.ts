export class FileDeleteFailed extends Error {
    readonly deleted: string[]

    readonly errors: { fileId: string; error: Error }[]

    constructor(deleted: string[], errors: { fileId: string; error: Error }[]) {
        super("Unable to delete some files")
        Object.setPrototypeOf(this, FileDeleteFailed.prototype)
        this.name = "FileDeleteFailed"
        this.deleted = deleted
        this.errors = errors
    }
}
