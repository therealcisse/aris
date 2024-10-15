import sbt.*

object Dependencies {

  val ZioJdbcVersion = "0.1.2"
  val ZioVersion = "2.1.9"
  val ZioCliVersion = "0.5.0"
  val ZioParserVersion = "0.1.10"
  val ZioSchemaVersion = "1.5.0"
  val SttpVersion = "3.3.18"
  val ZioConfigVersion = "4.0.2"
  val ZioInteropsCats = "23.0.0.2"
  val ZioPreludeVersion = "1.0.0-RC20"
  val FlywayVersion = "9.8.1"
  val HikariCPVersion = "5.0.1"
  val CUIDVersion = "2.0.3"
  val ZioJsonVersion = "0.7.3"
  val PostgresVersion = "42.5.0"
  val ULIDVersion = "1.0"
  val SL4JVersion = "2.0.13"
  val CatsVersion = "2.9.0"
  val CatsEffectKernelVersion = "3.5.4"

  val scalafmt = "org.scalameta" %% "scalafmt-dynamic" % "3.8.1"

  val flyway = "org.flywaydb" % "flyway-core" % "9.8.1"
  val hicariCP = "com.zaxxer" % "HikariCP" % HikariCPVersion

  val `slf4j-api` = "org.slf4j" % "slf4j-api" % "2.0.13"
  val `slf4j-simple` = "org.slf4j" % "slf4j-simple" % "2.0.13"

  val ulid = "dev.shamil" % "ulid4j" % ULIDVersion
  val `postgres-driver` = "org.postgresql" % "postgresql" % PostgresVersion

  val cats = "org.typelevel" %% "cats-core" % CatsVersion
  val `cats-effect-kernel` = "org.typelevel" %% "cats-effect" % CatsEffectKernelVersion

  val `zio-json` = "dev.zio" %% "zio-json" % ZioJsonVersion
  val `zio-jdbc` = "dev.zio" %% "zio-jdbc" % ZioJdbcVersion
  val `zio-prelude` = "dev.zio" %% "zio-prelude" % ZioPreludeVersion
  val `zio-interop-cats` = "dev.zio" %% "zio-interop-cats" % ZioInteropsCats
  val zio = "dev.zio" %% "zio" % ZioVersion
  val `zio-cli` = "dev.zio" %% "zio-cli" % ZioCliVersion
  val `zio-config` = "dev.zio" %% "zio-config" % ZioConfigVersion
  val `zio-config-magnolia` = "dev.zio" %% "zio-config-magnolia" % ZioConfigVersion
  val `zio-config-typesafe` = "dev.zio" %% "zio-config-typesafe" % ZioConfigVersion
  val `zio-json-yaml` = "dev.zio" %% "zio-json-yaml" % ZioJsonVersion
  val `zio-streams` = "dev.zio" %% "zio-streams" % ZioVersion
  val `zio-schema` = "dev.zio" %% "zio-schema" % ZioSchemaVersion
  val `zio-schema-json` = "dev.zio" %% "zio-schema-json" % ZioSchemaVersion
  val `zio-schema-protobuf` = "dev.zio" %% "zio-schema-protobuf" % ZioSchemaVersion
  val `zio-schema-avro` = "dev.zio" %% "zio-schema-avro" % ZioSchemaVersion
  val `zio-schema-thrift` = "dev.zio" %% "zio-schema-thrift" % ZioSchemaVersion
  val `zio-schema-msg-pack` = "dev.zio" %% "zio-schema-msg-pack" % ZioSchemaVersion
  val `zio-test` = "dev.zio" %% "zio-test" % ZioVersion % Test
  val `zio-test-sbt` = "dev.zio" %% "zio-test-sbt" % ZioVersion % Test
  val `zio-mock` = "dev.zio" %% "zio-mock" % "1.0.0-RC12" % Test
  val `zio-test-magnolia` = "dev.zio" %% "zio-test-magnolia" % "2.1.9" % Test
  val `testcontainers-scala-postgresql` = "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.41.4" % Test
  val `zio-http` = "dev.zio" %% "zio-http" % "3.0.1"

}
