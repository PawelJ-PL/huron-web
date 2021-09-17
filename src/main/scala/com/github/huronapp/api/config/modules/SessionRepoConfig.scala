package com.github.huronapp.api.config.modules

import cats.syntax.parallel._
import ciris.{ConfigDecoder, ConfigKey, ConfigValue, env}
import zio.Task

sealed trait SessionRepoConfig

object SessionRepoConfig {

  case object InMemorySessionRepo extends SessionRepoConfig

  final case class RedisSessionRepo(host: String, port: Int) extends SessionRepoConfig

  private val repoType = env("SESSIONS_REPOSITORY_TYPE").as[RepositoryType]

  private val redisRepoConfig: ConfigValue[Task, SessionRepoConfig] = (
    env("REDIS_HOST").as[String],
    env("REDIS_PORT").as[Int].default(6379)
  ).parMapN(RedisSessionRepo)

  val load: ConfigValue[Task, SessionRepoConfig] = repoType.flatMap {
    case RepositoryType.InMemory => ConfigValue.loaded(ConfigKey.env("SESSIONS_REPOSITORY_TYPE"), InMemorySessionRepo)
    case RepositoryType.Redis    => redisRepoConfig
  }

}

private sealed trait RepositoryType

private object RepositoryType {

  case object InMemory extends RepositoryType

  case object Redis extends RepositoryType

  implicit val configDecoder: ConfigDecoder[String, RepositoryType] = ConfigDecoder[String, String].collect("RepositoryType") {
    case "InMemory" => RepositoryType.InMemory
    case "Redis"    => RepositoryType.Redis
  }

}
