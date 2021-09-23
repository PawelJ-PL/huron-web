package com.github.huronapp.api.constants

import com.github.huronapp.api.domain.collections.CollectionId
import com.github.huronapp.api.domain.files.{Directory, EncryptionParams, File, FileId, FileVersionId}
import com.github.huronapp.api.domain.users.UserId
import io.chrisdavenport.fuuid.FUUID

import java.time.Instant

trait Files extends Collections with Users {

  final val ExampleDirectoryId = FileId(FUUID.fuuid("163cea59-bf07-46f7-8589-d997ba766545"))

  final val ExampleDirectoryName = "some dir"

  final val ExampleDirectoryMetadata =
    Directory(ExampleDirectoryId, CollectionId(ExampleCollectionId), None, ExampleDirectoryName, Instant.parse("2021-09-07T20:59:07Z"))

  final val ExampleFileId = FileId(FUUID.fuuid("1b495fe5-b66d-45de-9341-8dfa96ed01ad"))

  final val ExampleFileName = "file1.txt"

  final val ExampleFileDescription = "some file"

  final val ExampleFileVersionId = FileVersionId(FUUID.fuuid("4d99be77-ce28-4ae1-9270-6195ffaa726c"))

  final val ExampleFilePlainTextDigest = "someDigest"

  final val ExampleFileEncryptedDigest = "digest(788ca0)"

  final val ExampleFileContentAlgorithm = "AES-CBC"

  final val ExampleFileContentIv = "9c9732833179dbcdb0d973e3f1366c78"

  final val ExampleFileMetadata = File(
    ExampleFileId,
    CollectionId(ExampleCollectionId),
    None,
    ExampleFileName,
    Some(ExampleFileDescription),
    ExampleFileVersionId,
    Some(UserId(ExampleUserId)),
    3L,
    None,
    ExampleFilePlainTextDigest,
    ExampleFileEncryptedDigest,
    EncryptionParams(ExampleFileContentAlgorithm, ExampleFileContentIv, ExampleEncryptionKeyVersion),
    Instant.parse("2021-09-07T20:59:07Z"),
    Instant.parse("2021-09-07T20:59:07Z")
  )

  final val ExampleFileContent: Array[Byte] = Array(120.toByte, 140.toByte, 160.toByte)

}
