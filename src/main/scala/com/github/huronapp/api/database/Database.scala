package com.github.huronapp.api.database

import com.github.huronapp.api.config.DatabaseConfig
import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing

import java.sql.DriverManager
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import zio.blocking.Blocking
import zio.{Has, Task, ZIO, ZLayer, ZManaged}

object Database {

  type Database = Has[Database.Service]

  trait Service {

    def migrate: Task[Unit]

  }

  def migrate: ZIO[Database, Throwable, Unit] = ZIO.accessM[Database](_.get.migrate)

  val live: ZLayer[Has[DatabaseConfig] with Blocking with KamonTracing, Nothing, Database] =
    ZLayer.fromServices[DatabaseConfig, Blocking.Service, KamonTracing.Service, Database.Service]((dbConfig, blocking, tracing) =>
      new Service {

        private final val LiquibaseChangelogMaster = "db/changelog/changelog-master.yml"

        override def migrate: Task[Unit] =
          tracing.createSpan(
            "Database migration",
            (for {
              connection <- ZManaged.fromAutoCloseable(
                              blocking.effectBlocking(DriverManager.getConnection(dbConfig.url, dbConfig.username, dbConfig.password.value))
                            )
              database   <-
                ZManaged.make(
                  blocking.effectBlocking(DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))
                )(connection => blocking.effectBlocking(connection.close()).orDie)
              liquibase  <-
                ZManaged.fromEffect(
                  Task(new Liquibase(LiquibaseChangelogMaster, new ClassLoaderResourceAccessor(getClass.getClassLoader), database))
                )
            } yield liquibase).use(liquibase => blocking.effectBlocking(liquibase.update("main"))),
            Map.empty
          )

      }
    )

}
