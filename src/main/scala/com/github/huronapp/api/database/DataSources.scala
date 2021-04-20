package com.github.huronapp.api.database

import com.github.huronapp.api.config.DatabaseConfig
import com.zaxxer.hikari.HikariDataSource
import zio.{Has, ZIO, ZLayer, blocking}
import zio.blocking.Blocking

import javax.sql.DataSource

object DataSources {

  val hikari: ZLayer[Blocking with Has[DatabaseConfig], Throwable, Has[DataSource]] = ZIO
    .accessM[Blocking with Has[DatabaseConfig]] { env =>
      blocking.effectBlocking {
        val dbConf = env.get[DatabaseConfig]
        val ds = new HikariDataSource()
        ds.setJdbcUrl(dbConf.url)
        ds.setUsername(dbConf.username)
        ds.setPassword(dbConf.password.value)
        ds.setPoolName("HikariPool-HuronApp")
        ds.setConnectionTimeout(dbConf.connectionTimeout.toLong)
        ds.setMaximumPoolSize(dbConf.maxPoolSize)
        ds
      }
    }
    .toLayer

}
