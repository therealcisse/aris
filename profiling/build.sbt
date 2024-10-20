ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "3.5.1"
ThisBuild / organization := "com.youtoo"
ThisBuild / organizationName := "cqrs"

lazy val core = ProjectRef(file("/cqrs-example-ingestion/cqrs-example-ingestion-src"), "core")
lazy val postgres = ProjectRef(file("/cqrs-example-ingestion/cqrs-example-ingestion-src"), "postgres")
lazy val exampleIngestion = ProjectRef(file("/cqrs-example-ingestion/cqrs-example-ingestion-src"), "exampleIngestion")

lazy val root = (project in file("."))
  .settings(
    name := "cqrs-profiling",
    fork := true,
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(
    core,
    postgres,
    exampleIngestion,
  )
