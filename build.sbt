import BuildHelper.*
import Dependencies.{scalafmt, *}

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

// Setting default log level to INFO
val _ = sys.props += ("CQRSLogLevel" -> Debug.CQRSLogLevel)

lazy val aggregatedProjects: Seq[ProjectReference] =
  Seq(
    core,
    postgres,
    exampleIngestion,
  )

lazy val root = (project in file("."))
  .settings(stdSettings("cqrs-root"))
  .settings(publishSetting(false))
  .settings(meta)
  .aggregate(aggregatedProjects *)

lazy val core = (project in file("cqrs-core"))
  .settings(stdSettings("cqrs-core"))
  .settings(publishSetting(false))
  .settings(
    libraryDependencies ++= Seq(
      `zio-jdbc`,
      `zio-schema-protobuf`,
      cats,
      ulid,
      zio,
      `zio-prelude`,
      `zio-schema`,
    ),
  )

lazy val postgres = (project in file("cqrs-persistence-postgres"))
  .settings(stdSettings("cqrs-persistence-postgres"))
  .settings(publishSetting(false))
  .settings(
    libraryDependencies ++= Seq(
      `zio-jdbc`,
      `slf4j-api`,
      `slf4j-simple`,
    ),
  )
  .dependsOn(core)

lazy val exampleIngestion = (project in file("cqrs-example-ingestion"))
  .settings(stdSettings("cqrs-example-ingestion"))
  .settings(publishSetting(false))
  .settings(
    libraryDependencies ++= Seq(
      `postgres-driver`,
      `zio-json`,
      flyway,
      hicariCP,
    ),
  )
  .dependsOn(postgres, core)
