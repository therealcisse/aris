import sbt.*

object Dependencies {

  val ZioJdbcVersion = "0.1.2"
  val ZioVersion = "2.1.12"
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
  val GatlingVersion = "3.12.0"

  val scalafmt = "org.scalameta" %% "scalafmt-dynamic" % "3.8.1"

  val flyway = "org.flywaydb" % "flyway-core" % "9.8.1"
  val hicariCP = "com.zaxxer" % "HikariCP" % HikariCPVersion

  val `slf4j-api` = "org.slf4j" % "slf4j-api" % "2.0.16"
  val `slf4j-simple` = "org.slf4j" % "slf4j-simple" % "2.0.13"
  val `slf4j-log4j12` = "org.slf4j" % "slf4j-log4j12" % "2.0.16"

  val `postgres-driver` = "org.postgresql" % "postgresql" % PostgresVersion

  val cats = "org.typelevel" %% "cats-core" % CatsVersion
  val `cats-effect-kernel` = "org.typelevel" %% "cats-effect" % CatsEffectKernelVersion

  val `zio-metrics-connectors-prometheus` = "dev.zio" %% "zio-metrics-connectors-prometheus" % "2.3.1"
  val `zio-metrics` = "dev.zio" %% "zio-metrics" % "2.0.1"
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
  val `zio-test-magnolia` = "dev.zio" %% "zio-test-magnolia" % ZioVersion % Test
  val `testcontainers-scala-postgresql` = "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.41.4" % Test
  val `zio-logging` = "dev.zio" %% "zio-logging" % "2.3.2"
  val `zio-logging-slf4j` = "dev.zio" %% "zio-logging-slf4j" % "2.3.1"
  val `zio-logging-slf4j2` = "dev.zio" %% "zio-logging-slf4j2" % "2.3.2"
  val logback = "ch.qos.logback" % "logback-classic" % "1.5.12"
  val `logback-core` = "ch.qos.logback" % "logback-core" % "1.5.12"

  val `gatling-charts-highcharts` = "io.gatling.highcharts" % "gatling-charts-highcharts" % GatlingVersion % Test
  val `gatling-test-framework` = "io.gatling" % "gatling-test-framework" % GatlingVersion % Test

  val `scala-collection-compat` =
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.12.0" cross CrossVersion.for3Use2_13

  val mockito = "org.mockito" % "mockito-core" % "5.14.2" % Test

  val pprint = "com.lihaoyi" %% "pprint" % "0.9.0"

  val `scala-collection-contrib` = "org.scala-lang.modules" %% "scala-collection-contrib" % "0.4.0"

  val `hadoop-aws` = "org.apache.hadoop" % "hadoop-aws" % "3.4.1"
  val `hadoop-client` = "org.apache.hadoop" % "hadoop-client" % "3.4.1"

  val netty = "io.netty" % "netty-all" % "4.1.114.Final"

  val jansi = "org.fusesource.jansi" % "jansi" % "2.4.0"

  val `logstash-logback-encoder` = "net.logstash.logback" % "logstash-logback-encoder" % "8.0"

  val `zio-telemetry` = "dev.zio" %% "zio-opentelemetry" % "3.0.1"

  val `open-telemetry` = Seq(
    "dev.zio" %% "zio-http" % "3.0.1",
    // "io.zipkin.reporter2" % "zipkin-reporter" % "2.16.3",
    // "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "2.16.3",
    "dev.zio" %% "zio-opentelemetry" % "3.0.1",
    // "dev.zio" %% "zio-opentelemetry-zio-logging" % "3.0.1",
    "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.43.0",
    "io.opentelemetry" % "opentelemetry-exporter-logging-otlp" % "1.43.0",
    // "io.opentelemetry" % "opentelemetry-exporter-zipkin" % "1.43.0",
    "io.opentelemetry" % "opentelemetry-sdk" % "1.43.0",
    "io.opentelemetry" % "opentelemetry-sdk-trace" % "1.43.0",
    "io.opentelemetry.semconv" % "opentelemetry-semconv" % "1.22.0-alpha",
    "io.opentelemetry" % "opentelemetry-sdk-testing" % "1.43.0" % Test,
    "io.grpc" % "grpc-netty-shaded" % "1.47.0",
  )
}
