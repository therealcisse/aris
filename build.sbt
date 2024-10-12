import BuildHelper.*
import Dependencies.{scalafmt, *}

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

// Setting default log level to INFO
val _ = sys.props += ("CQRSLogLevel" -> Debug.CQRSLogLevel)

lazy val aggregatedProjects: Seq[ProjectReference] =
  Seq(
    cqrsCore,
  )

lazy val root = (project in file("."))
  .settings(stdSettings("cqrs-root"))
  .settings(publishSetting(false))
  .settings(meta)
  .aggregate(aggregatedProjects *)

lazy val cqrsCore = (project in file("cqrs-core"))
  .settings(stdSettings("cqrs-core"))
  .settings(publishSetting(false))
  .settings(
    libraryDependencies ++= Seq(
      zio,
      cuid,
      `zio-prelude`,
      `zio-schema`,
      "org.slf4j" % "slf4j-api"    % "2.0.13",
      "org.slf4j" % "slf4j-simple" % "2.0.13",
    ),
  )

lazy val cqrsPostgres = (project in file("cqrs-persistence-postgres"))
  .settings(stdSettings("cqrs-persistence-postgres"))
  .settings(publishSetting(false))
  .settings(
    libraryDependencies ++= Seq(
      zio,
      `zio-jdbc`,
      `zio-schema`,
      `zio-schema-protobuf`,
      `zio-interop-cats`,
      "org.slf4j" % "slf4j-api"    % "2.0.13",
      "org.slf4j" % "slf4j-simple" % "2.0.13",
    ),
  )
  .dependsOn(cqrsCore)

lazy val cqrsExampleIngestion = (project in file("cqrs-example-ingestion"))
  .settings(stdSettings("cqrs-example-ingestion"))
  .settings(publishSetting(false))
  .settings(
    libraryDependencies ++= Seq(
      postgres,
      `zio-json`,
      cuid,
      flyway,
      hicariCP,
      zio,
      `zio-prelude`,
      `zio-jdbc`,
      `zio-schema`,
      `zio-schema-protobuf`,
      `zio-interop-cats`,
      "org.slf4j" % "slf4j-api"    % "2.0.13",
      "org.slf4j" % "slf4j-simple" % "2.0.13",
    ),
  )
  .dependsOn(cqrsPostgres, cqrsCore)
