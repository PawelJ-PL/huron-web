import { DirectoryMetadata, FilesystemUnitMetadata, FileMetadata } from "./FilesystemUnitMetadata"

export type FileTree = {
    metadata: FileMetadata
    parents: DirectoryMetadata[]
}

export type DirectoryTree = {
    metadata: DirectoryMetadata
    parents: DirectoryMetadata[]
    children: FilesystemUnitMetadata[]
}

export type RootDirectoryTree = {
    children: FilesystemUnitMetadata[]
}

export type ObjectTree = FileTree | DirectoryTree | RootDirectoryTree

export function isFileTree(obj: ObjectTree): obj is FileTree {
    return "metadata" in obj && obj.metadata["@type"] === "FileData"
}

export function isDirectoryTree(obj: ObjectTree): obj is DirectoryTree {
    return "metadata" in obj && obj.metadata["@type"] === "DirectoryData"
}

export function isRootDirectoryTree(obj: ObjectTree): obj is RootDirectoryTree {
    return !("metadata" in obj)
}
