import BuildHelper.*
import Dependencies.*

import com.github.sbt.git.SbtGit.git

import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoPlugin.autoImport.*

import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.*
import com.typesafe.sbt.packager.docker.*

import sbt.Keys.*

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

ThisBuild / version := git.gitHeadCommit.value.map(commit => s"1.0.0-${commit.take(7)}").getOrElse("1.0.0-SNAPSHOT")
ThisBuild / git.useGitDescribe := true

ThisBuild / Test / javaOptions ++= Seq(
  "-Dlogback.configurationFile=logback-test.xml",
  )

ThisBuild / git.gitTagToVersionNumber := { tag: String =>
  if (tag matches "[0-9]+\\..*") Some(tag)
  else None
}

// Define onLoad task to copy the hook into .git/hooks/pre-push
def installGitHook: Unit = if (!sys.env.contains("CI")) {
  import java.nio.file.{Files, Paths, StandardCopyOption}

  val hookSource = Paths.get("hooks/prepush")
  val hookTarget = Paths.get(".git/hooks/pre-push")

  if (Files.exists(hookSource)) {
    Files.copy(hookSource, hookTarget, StandardCopyOption.REPLACE_EXISTING)
    println("Git pre-push hook installed successfully.")
  } else {
    println("Git hook source not found. Skipping installation.")
  }
}

// Register the onLoad command to install the hook
onLoad in Global := {
  val previousOnLoad = (Global / onLoad).value
  state => {
    installGitHook
    previousOnLoad(state)
  }
}
lazy val aggregatedProjects: Seq[ProjectReference] =
  Seq(
    core,
    postgres,
    memory,
  )

inThisBuild(replSettings)

lazy val root = (project in file("."))
  .settings(git.useGitDescribe := true)
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("aris"))
  // .settings(publishSetting(false))
  .settings(meta)
  .aggregate(aggregatedProjects *)

lazy val core = (project in file("core"))
  .settings(stdSettings("core"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      cats,
      // pprint,
      `zio-schema-protobuf`,
      `zio-schema-json`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val postgres = (project in file("postgres"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(stdSettings("postgres"))
  // .settings(publishSetting(false))
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
    libraryDependencies ++= db,
    libraryDependencies ++= Seq(
      cats,
      mockito,
      `zio-jdbc`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val memory = (project in file("memory"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(stdSettings("memory"))
  // .settings(publishSetting(false))
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
    libraryDependencies += `scala-collection-contrib`,
    libraryDependencies ++= Seq(
      cats,
      `zio-jdbc`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
    ),
  )

