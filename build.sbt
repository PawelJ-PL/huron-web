val dependencies = {
  val plugins = Seq(
    compilerPlugin("com.olegpy" %% "better-monadic-for" % Versions.betterMonadicFor)
  )

  val zio = Seq(
    "dev.zio" %% "zio" % Versions.zio,
    "dev.zio" %% "zio-interop-cats" % Versions.zioCatsInterop,
    "dev.zio" %% "zio-macros" % Versions.zio
  ) ++ Seq(
    "dev.zio" %% "zio-test",
    "dev.zio" %% "zio-test-sbt"
  ).map(_ % Versions.zio % "test") ++ Seq(
    "io.github.kitlangton" %% "zio-magic" % Versions.zioMagic
  ) ++ Seq(
    "io.github.gaelrenoux" %% "tranzactio" % Versions.tranzactio
  ) ++ Seq(
    "dev.zio" %% "zio-logging",
    "dev.zio" %% "zio-logging-slf4j"
  ).map(_ % Versions.zioLogging)

  val http4s = Seq(
    "org.http4s" %% "http4s-core",
    "org.http4s" %% "http4s-blaze-server",
    "org.http4s" %% "http4s-dsl"
  ).map(_ % Versions.http4s) ++ Seq( "org.http4s" %% "http4s-circe" % Versions.http4s % "test")

  val logger = Seq(
    "ch.qos.logback" % "logback-classic" % Versions.logback
  )

  val ciris = Seq(
    "is.cir" %% "ciris"
  ).map(_ % Versions.ciris)

  val tapir = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-zio",
    "com.softwaremill.sttp.tapir" %% "tapir-zio-http4s-server",
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe",
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs",
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml",
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s",
    "com.softwaremill.sttp.tapir" %% "tapir-enumeratum"
  ).map(_ % Versions.tapir)

  val circe = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-shapes",
    "io.circe" %% "circe-generic-extras"
  ).map(_ % Versions.circe)

  val cats = Seq(
    "org.typelevel" %% "cats-core" % Versions.cats
  )

  val semver = Seq(
    "com.vdurmont" % "semver4j" % Versions.semver4j
  )

  val database = Seq(
    "org.liquibase" % "liquibase-core" % Versions.liquibase,
    "org.postgresql" % "postgresql" % Versions.postgresDriver
  )

  val doobie = Seq(
    "org.tpolecat" %% "doobie-core",
    "org.tpolecat" %% "doobie-hikari",
    "org.tpolecat" %% "doobie-quill",
    "org.tpolecat" %% "doobie-postgres"
  ).map(_ % Versions.doobie)

  val chimney = Seq(
    "io.scalaland" %% "chimney" % Versions.chimney
  )

  val fuuid = Seq(
    "io.chrisdavenport" %% "fuuid",
    "io.chrisdavenport" %% "fuuid-circe",
    "io.chrisdavenport" %% "fuuid-http4s"
  ).map(_ % Versions.fuuid)

  val crypt = Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % Versions.bouncyCastle
  )

  val enumeratum = Seq(
    "com.beachape" %% "enumeratum" % Versions.enumeratum,
    "com.beachape" %% "enumeratum-circe" % Versions.enumeratum,
    "com.beachape" %% "enumeratum-cats" % Versions.enumeratum
  )

  val templating = Seq(
    "com.github.eikek" %% "yamusca-core" % Versions.yamusca
  )

  val scalaCache = Seq(
    "com.github.cb372" %% "scalacache-core" % Versions.scalaCache,
    "com.github.cb372" %% "scalacache-caffeine" % Versions.scalaCache,
    "com.github.cb372" %% "scalacache-redis" % Versions.scalaCache,
    "com.github.cb372" %% "scalacache-circe" % Versions.scalaCache
  )

  val kamon = Seq(
    "io.kamon" %% "kamon-core",
    "io.kamon" %% "kamon-executors",
    "io.kamon" %% "kamon-jdbc",
    "io.kamon" %% "kamon-logback",
    "io.kamon" %% "kamon-system-metrics",
    "io.kamon" %% "kamon-prometheus",
    "io.kamon" %% "kamon-zipkin"
  ).map(_ % Versions.kamon) ++ Seq(
    "io.kamon" %% "kamon-http4s" % Versions.kamonHttp4s
  )

  libraryDependencies ++= plugins ++ zio ++ http4s ++ logger ++ ciris ++ tapir ++ circe ++ cats ++ semver ++ database ++ doobie ++ chimney ++ fuuid ++ crypt ++ enumeratum ++ templating ++ scalaCache ++ kamon
}

val compilerOptions = scalacOptions ~= (_.filterNot(Set("-Xfatal-warnings")).+:("-Ymacro-annotations"))

val root = (project in file("."))
  .settings(
    name := "huron-web",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.5",
    compilerOptions,
    dependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
