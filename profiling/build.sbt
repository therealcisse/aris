ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "3.5.1"
ThisBuild / organization := "com.youtoo"
ThisBuild / organizationName := "cqrs"

lazy val core = ProjectRef(file("/ingestion/ingestion-src"), "core")
lazy val postgres = ProjectRef(file("/ingestion/ingestion-src"), "postgres")
lazy val ingestion = ProjectRef(file("/ingestion/ingestion-src"), "ingestion")

lazy val root = (project in file("."))
  .settings(
    name := "cqrs-profiling",
    fork := true,
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(
    core,
    postgres,
    ingestion,
  )
