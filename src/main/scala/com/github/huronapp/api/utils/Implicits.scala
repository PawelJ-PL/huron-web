package com.github.huronapp.api.utils

import cats.syntax.either._
import ciris.{ConfigDecoder, ConfigError}
import com.github.huronapp.api.domain.collections.{CollectionId, CollectionPermission}
import com.github.huronapp.api.domain.files.{FileId, FileVersionId}
import com.github.huronapp.api.domain.users.UserId
import com.vdurmont.semver4j.Semver
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import io.chrisdavenport.fuuid.FUUID
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema}

object Implicits {

  object semver {

    implicit val semverEncoder: Encoder[Semver] = Encoder.encodeString.contramap(_.getValue)

    implicit val semverDecoder: Decoder[Semver] = Decoder.decodeString.emap(SemverWrapper.of(_).leftMap(_.getMessage))

    implicit val semverTapirSchema: Schema[Semver] = Schema.string

    implicit val semverConfigDecoder: ConfigDecoder[String, Semver] =
      ConfigDecoder.instance((maybeKey, value) =>
        SemverWrapper.of(value).leftMap(err => ConfigError(maybeKey.toString + ": " + err.getMessage))
      )

  }

  object fuuid {

    implicit val fuuidTapirSchema: Schema[FUUID] = Schema.schemaForString.map(FUUID.fromStringOpt)(_.show)

    implicit val fuuidTapirTextPlainCodec: Codec[String, FUUID, CodecFormat.TextPlain] = Codec
      .string
      .mapDecode(value =>
        FUUID.fromString(value) match {
          case Left(error)  => DecodeResult.Error(value, error)
          case Right(fuuid) => DecodeResult.Value(fuuid)
        }
      )(_.show)

  }

  object userId {

    implicit val userIdTapirTextPlainCodec: Codec[String, UserId, CodecFormat.TextPlain] =
      fuuid.fuuidTapirTextPlainCodec.map(UserId(_))(_.id)

    implicit val userIdTapirSchema: Schema[UserId] = Schema.schemaForString.map(FUUID.fromStringOpt(_).map(UserId(_)))(_.id.show)

    implicit val userIdEncoder: Encoder[UserId] = Encoder.encodeString.contramap(_.id.show)

    implicit val userIdDecoder: Decoder[UserId] = Decoder.decodeString.emap(FUUID.fromString(_).bimap(_.getMessage, UserId(_)))

  }

  object collectionId {

    implicit val collectionIdTapirTextPlainCodec: Codec[String, CollectionId, CodecFormat.TextPlain] =
      fuuid.fuuidTapirTextPlainCodec.map(CollectionId(_))(_.id)

  }

  object fileId {

    implicit val fileIdTapirTextPlainCodec: Codec[String, FileId, CodecFormat.TextPlain] =
      fuuid.fuuidTapirTextPlainCodec.map(FileId(_))(_.id)

  }

  object versionId {

    implicit val versionIdTapirTextPlainCodec: Codec[String, FileVersionId, CodecFormat.TextPlain] =
      fuuid.fuuidTapirTextPlainCodec.map(FileVersionId(_))(_.id)

  }

  object fuuidKeyMap {

    implicit val FuuidMapKeyDecoder: KeyDecoder[FUUID] = KeyDecoder.instance(FUUID.fromStringOpt)

    implicit val FuuidMapKeyEncoder: KeyEncoder[FUUID] = KeyEncoder.instance(_.show)

    implicit def tapirSchemaForFuuidKeyMap[V: Schema]: Schema[Map[FUUID, V]] = Schema.schemaForMap[FUUID, V](_.show)

  }

  object fuuidCollectionPermissionsMap {
    import sttp.tapir.codec.enumeratum._

    implicit val FuuidMapKeyDecoder: KeyDecoder[FUUID] = KeyDecoder.instance(FUUID.fromStringOpt)

    implicit val FuuidMapKeyEncoder: KeyEncoder[FUUID] = KeyEncoder.instance(_.show)

    implicit val tapirSchemaForFuuidKeyMap: Schema[Map[FUUID, List[CollectionPermission]]] =
      Schema.schemaForMap[FUUID, List[CollectionPermission]](_.show)

  }

  object nonEmptyList {

    implicit def nonEmptyListEncoder[T: Encoder]: Encoder[List[T] Refined NonEmpty] = Encoder.encodeList[T].contramap(_.value)

    implicit def nonEmptyListDecoder[T: Decoder]: Decoder[List[T] Refined NonEmpty] = Decoder.decodeList[T].emap(refineV(_))

  }

  object collectionPermission {

    implicit val collectionPermissionTapirSchema: Schema[CollectionPermission] =
      Schema.schemaForString.map(CollectionPermission.withNameOption)(_.entryName)

  }

}
