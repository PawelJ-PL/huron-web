import { FileVersion } from "./../../domain/file/types/FileVersion"
import { exampleUserId } from "./user"
import { exampleCollectionId } from "./collection"
import { DirectoryMetadata, FileMetadata } from "../../domain/file/types/FilesystemUnitMetadata"
import { DirectoryTree, FileTree } from "../../domain/file/types/ObjectTree"

export const exampleDirectoryId = "99d68922-4efa-4824-b08d-a224f3b3b7d9"

export const exampleFileId = "99de9649-ea1c-4002-95e0-18a66cdda224"

export const exampleVersionId = "21e6f577-cd53-4703-a8e6-275944f7b7aa"

export const exampleDirectoryName = "example-dir"

export const exampleFileName = "file.pdf"

export const exampleCurrentDirectory: DirectoryMetadata = {
    "@type": "DirectoryData",
    id: "2addf5f0-82e1-4a90-b4c4-9151617d4c58",
    name: "some-dir",
    collectionId: exampleCollectionId,
    parent: null,
}

export const exampleChildDirectory1: DirectoryMetadata = {
    "@type": "DirectoryData",
    id: "d13b215d-858a-45cc-bc82-057f33d9ee6e",
    name: "bbb-dir",
    parent: exampleCurrentDirectory.id,
    collectionId: exampleCollectionId,
}

export const exampleChildDirectory2: DirectoryMetadata = {
    "@type": "DirectoryData",
    id: "11dcb27d-6d89-4779-b425-750531bb7884",
    name: "xxx-dir",
    parent: exampleCurrentDirectory.id,
    collectionId: exampleCollectionId,
}

export const exampleChildFile1: FileMetadata = {
    "@type": "FileData",
    id: "2dc81e5b-0723-4c5c-8a99-3ef2feb17b27",
    name: "bbb-file",
    collectionId: exampleCollectionId,
    versionId: "fcb43acb-44ff-467c-972f-1f32dd564f56",
    encryptedSize: 100,
    contentDigest: "0e8363c37e4bba4875921a3beadebf6feac59d3476a3ff0fa467b6b59973d17c",
    updatedAt: "2021-07-02T21:02:38.475Z",
    parent: exampleCurrentDirectory.id,
}

export const exampleChildFile2: FileMetadata = {
    "@type": "FileData",
    id: "13e8063a-f1c4-49f8-b4cc-e027272f1de7",
    name: "xxx-file",
    collectionId: exampleCollectionId,
    versionId: "39edd13b-5d9e-4ef2-9de6-952d3d423824",
    encryptedSize: 1024,
    contentDigest: "e984bcbf9daa3b74b683760120cb8b8a7a6b3cfc1d82ad56d69ad893b422d1b2",
    updatedAt: "2021-07-02T21:02:38.475Z",
    parent: exampleCurrentDirectory.id,
}

export const exampleDirectoryTree: DirectoryTree = {
    metadata: exampleCurrentDirectory,
    parents: [],
    children: [exampleChildDirectory1, exampleChildDirectory2, exampleChildFile1, exampleChildFile2],
}

export const exampleDirectoryData: DirectoryMetadata = {
    parent: exampleCurrentDirectory.id,
    id: exampleDirectoryId,
    collectionId: exampleCollectionId,
    name: exampleDirectoryName,
    "@type": "DirectoryData",
}

export const exampleFileContent = new File([new Uint8Array([122, 123, 124, 125])], exampleFileName)

export const exampleFileData: FileMetadata = {
    id: exampleFileId,
    collectionId: exampleCollectionId,
    parent: exampleCurrentDirectory.id,
    name: exampleFileName,
    versionId: exampleVersionId,
    versionAuthor: exampleUserId,
    mimeType: "application/pdf",
    contentDigest: "2ef460f0e8055181a2b8d8679bdfef276f7872a7db2e7d577798b4c34698e94a",
    encryptedSize: 4096,
    updatedAt: "2021-07-02T21:02:38.475Z",
    "@type": "FileData",
}

export const exampleFileTree: FileTree = {
    metadata: exampleFileData,
    parents: [],
}

export const exampleVersion: FileVersion = {
    id: exampleFileData.id,
    collectionId: exampleFileData.collectionId,
    versionId: exampleVersionId,
    versionAuthor: exampleUserId,
    mimeType: exampleFileData.mimeType,
    contentDigest: exampleFileData.contentDigest,
    encryptedSize: exampleFileData.encryptedSize,
    updatedAt: exampleFileData.updatedAt,
}
