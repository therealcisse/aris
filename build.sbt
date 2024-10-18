import BuildHelper.*
import Dependencies.*

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

ThisBuild / version := "0.1.0-SNAPSHOT"

// Setting default log level to INFO
val _ = sys.props += ("CQRSLogLevel" -> Debug.CQRSLogLevel)

lazy val aggregatedProjects: Seq[ProjectReference] =
  Seq(
    core,
    postgres,
    exampleIngestion,
    benchmark,
  )

inThisBuild(
  replSettings,
)

lazy val root = (project in file("."))
  .settings(stdSettings("cqrs-root"))
  .settings(publishSetting(false))
  .settings(meta)
  .aggregate(aggregatedProjects *)

lazy val core = (project in file("cqrs-core"))
  .settings(stdSettings("cqrs-core"))
  .settings(publishSetting(false))
  .settings(buildInfoSettings("cqrs"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      `zio-logging`,
      `zio-logging-slf4j`,
      logback,
      `zio-jdbc`,
      `zio-schema-protobuf`,
      `zio-schema-json`,
      cats,
      ulid,
      zio,
      `zio-prelude`,
      `zio-schema`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val postgres = (project in file("cqrs-persistence-postgres"))
  .settings(stdSettings("cqrs-persistence-postgres"))
  .settings(publishSetting(false))
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
    libraryDependencies ++= Seq(
      flyway,
      hicariCP,
      `postgres-driver`,
      `testcontainers-scala-postgresql`,
      `zio-jdbc`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
      mockito,
    ),
  )
  .dependsOn(core)

lazy val exampleIngestion = (project in file("cqrs-example-ingestion"))
  .settings(stdSettings("cqrs-example-ingestion"))
  .settings(publishSetting(false))
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      `zio-metrics`,
      `zio-metrics-connectors-prometheus`,
      `testcontainers-scala-postgresql`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
      `zio-http`,
      `zio-json`,
    ),
  )
  .dependsOn(postgres % "compile->compile;test->test", core % "compile->compile;test->test")

lazy val benchmark = (project in file("cqrs-benchmark"))
  .settings(stdSettings("cqrs-benchmark"))
  .settings(publishSetting(false))
  .settings(Gatling / javaOptions := overrideDefaultJavaOptions("-Xms1G", "-Xmx4G"))
  .enablePlugins(GatlingPlugin)
  .settings(
    excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_2.13",
    libraryDependencies ++= Seq(
      `gatling-charts-highcharts`,
      `gatling-test-framework`,
    ),
  )
  .dependsOn(exampleIngestion)
