ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "3.5.1"
ThisBuild / organization := "com.youtoo"
ThisBuild / organizationName := "youtoo"

lazy val core = ProjectRef(file("/youtoo/youtoo-src"), "core")
lazy val std = ProjectRef(file("/youtoo/youtoo-src"), "std")
lazy val postgres = ProjectRef(file("/youtoo/youtoo-src"), "postgres")
lazy val ingestion = ProjectRef(file("/youtoo/youtoo-src"), "ingestion")
lazy val migration = ProjectRef(file("/youtoo/youtoo-src"), "dataMigration")

lazy val root = (project in file("."))
  .settings(
    name := "youtoo-profiling",
    fork := true,
  )
  .settings(
    Compile / run / javaOptions ++= Seq(
      "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
      "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
      "--add-exports=java.base/sun.security.x509=ALL-UNNAMED",
      "--add-exports=java.base/sun.security.util=ALL-UNNAMED",
    ),
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(
    core,
    std,
    postgres,
    ingestion,
    migration,
  )
