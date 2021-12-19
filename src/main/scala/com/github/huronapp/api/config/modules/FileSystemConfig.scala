package com.github.huronapp.api.config.modules

import cats.syntax.parallel._
import ciris.{ConfigDecoder, ConfigValue, env}
import zio.Task
import zio.nio.file.Path

sealed trait FileSystemConfig

object FileSystemConfig {

  final case class LocalFsConfig(root: Path, createMissingRoot: Boolean) extends FileSystemConfig

  private val fsType = env("FILESYSTEM_TYPE").as[FsType]

  private val localFsCfg: ConfigValue[Task, FileSystemConfig] = (
    env("FILESYSTEM_ROOT").as[String].map(Path(_)),
    env("CREATE_MISSING_ROOT_FS").as[Boolean].default(true)
  ).parMapN(LocalFsConfig)

  val load: ConfigValue[Task, FileSystemConfig] = fsType.flatMap {
    case FsType.LocalFs => localFsCfg
  }

}

private sealed trait FsType

private object FsType {

  case object LocalFs extends FsType

  implicit val configDecoder: ConfigDecoder[String, FsType] = ConfigDecoder[String, String].collect("FsType") {
    case "LocalFs" => FsType.LocalFs
  }

}
