package com.github.huronapp.api.domain.files

import com.github.huronapp.api.domain.collections.CollectionId
import com.github.huronapp.api.domain.users.UserId
import io.chrisdavenport.fuuid.FUUID

import java.time.Instant

sealed trait StorageUnit {

  val id: FileId

  val collectionId: CollectionId

  val parentId: Option[FileId]

  val name: String

}

final case class EncryptionParams(algorithm: String, iv: String, encryptionKeyVersion: FUUID)

final case class File(
  id: FileId,
  collectionId: CollectionId,
  parentId: Option[FileId],
  name: String,
  versionId: FileVersionId,
  versionAuthor: Option[UserId],
  size: Long,
  mimeType: Option[String],
  originalDigest: String,
  encryptedDigest: String,
  encryptionParams: EncryptionParams,
  createdAt: Instant,
  updatedAt: Instant)
    extends StorageUnit

final case class Directory(
  id: FileId,
  collectionId: CollectionId,
  parentId: Option[FileId],
  name: String,
  createdAt: Instant)
    extends StorageUnit
