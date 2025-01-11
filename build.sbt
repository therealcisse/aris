import BuildHelper.*
import Dependencies.*

import com.github.sbt.git.SbtGit.git

import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoPlugin.autoImport.*

import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.*
import com.typesafe.sbt.packager.docker.*

import sbt.Keys.*

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

ThisBuild / version := git.gitHeadCommit.value.map(commit => s"0.1.0-${commit.take(7)}").getOrElse("0.1.0-SNAPSHOT")
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
    mailApp,
    mail,
    sinkApp,
    sink,
    sourceApp,
    source,
    job,
    kernel,
    core,
    std,
    postgres,
    memory,
    ingestionApp,
    ingestion,
    dataMigrationApp,
    dataMigration,
    loadtests,
    benchmarks,
    log,
    observability,
    lock,
    `cqrs-persistence-postgres`,
    jobApp,
    lockApp,
  )

inThisBuild(replSettings)

lazy val root = (project in file("."))
  .settings(git.useGitDescribe := true)
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("youtoo"))
  // .settings(publishSetting(false))
  .settings(meta)
  .aggregate(aggregatedProjects *)

lazy val log = (project in file("log"))
  .settings(stdSettings("log"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .settings(
    libraryDependencies ++= Seq(
      jansi,
      `zio-telemetry`,
      `logstash-logback-encoder`,
      `slf4j-api`,
      `zio-logging`,
      `zio-logging-slf4j`,
      logback,
      `logback-core`,
    ),
  )

lazy val lock = (project in file("lock"))
  .settings(stdSettings("lock"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .dependsOn(
    core % "compile->compile;test->compile",
    postgres % "compile->compile;test->compile;test->test",
  )
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Dependencies.`open-telemetry`,
    libraryDependencies ++= Dependencies.db,
    libraryDependencies ++= Seq(
      `zio-jdbc`,
      zio,
      cats,
      `zio-prelude`,
      `zio-schema`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val kernel = (project in file("kernel"))
  .settings(stdSettings("kernel"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .dependsOn(log % "compile->compile;test->compile")
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      `scala-collection-contrib`,
      // pprint,
      zio,
      cats,
      `zio-prelude`,
      `zio-schema`,
      zio,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val postgres = (project in file("postgres"))
  .settings(stdSettings("postgres"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .dependsOn(log % "compile->compile;test->compile")
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Dependencies.`open-telemetry`,
    libraryDependencies ++= Dependencies.db,
    libraryDependencies ++= Seq(
      mockito,
      cats,
      `zio-jdbc`,
      `zio-jdbc`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val core = (project in file("cqrs-core"))
  .dependsOn(kernel)
  .settings(stdSettings("cqrs-core"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      // pprint,
      `zio-jdbc`,
      `zio-schema-protobuf`,
      `zio-schema-json`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
      mockito,
    ),
  )
  .dependsOn(kernel)

lazy val observability = (project in file("observability"))
  .dependsOn(kernel)
  .settings(stdSettings("observability"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Dependencies.`open-telemetry`,
    libraryDependencies += cats,
    libraryDependencies += `zio-prelude`,
    libraryDependencies += `zio`,
  )

lazy val std = (project in file("std"))
  .dependsOn(kernel)
  .settings(stdSettings("std"))
  .settings(
    Test / parallelExecution := false,
    testFrameworks += new TestFramework("zio.test.sbt.ztestframework"),
    libraryDependencies ++= Dependencies.`open-telemetry`,
    excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_2.13",
    libraryDependencies ++= Seq(
      `hadoop-client`,
      cronUtils,
      cats,
      zio,
      `zio-prelude`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val `cqrs-persistence-postgres` = (project in file("cqrs-persistence-postgres"))
  .dependsOn(
    core % "compile->test",
    postgres % "compile->compile;test->compile;test->test",
  )
  .settings(stdSettings("cqrs-persistence-postgres"))
  // .settings(publishSetting(false))
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
    libraryDependencies ++= Dependencies.`open-telemetry`,
    libraryDependencies ++= Seq(
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val memory = (project in file("cqrs-persistence-memory"))
  .dependsOn(
    core % "compile->test",
    postgres % "compile->compile;test->compile;test->test",
  )
  .settings(stdSettings("cqrs-persistence-memory"))
  // .settings(publishSetting(false))
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
    libraryDependencies ++= Dependencies.`open-telemetry`,
    libraryDependencies += `scala-collection-contrib`,
    libraryDependencies ++= Seq(
      `zio-jdbc`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
    ),
  )

lazy val ingestion = (project in file("ingestion"))
  .dependsOn(
    sink % "compile->compile;test->test",
    source % "compile->compile;test->test",
    memory % "compile->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
    core % "compile->compile;test->test",
    log % "compile->compile;test->compile",
    observability % "compile->compile",
  )
  .settings(stdSettings("ingestion"))
  .settings(

    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      netty,
      `hadoop-client`,
      `hadoop-aws`,
      `zio-metrics-connectors-prometheus`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val ingestionApp = (project in file("ingestion-app"))
  .dependsOn(
    std % "compile->compile;test->test",
    ingestion % "compile->compile;test->test",
    memory % "compile->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
    core % "compile->compile;test->test",
    log % "compile->compile;test->compile",
    observability % "compile->compile",
  )
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(
    Docker / packageName := "youtoo-ingestion",
    dockerBaseImage := "eclipse-temurin:17-jre",
    dockerExposedPorts := Seq(8181),
    dockerUpdateLatest := true,
    dockerEnvVars ++= BuildHelper.getEnvVars(),
    bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=logback-production.xml"""",
    mainClass := Some("com.youtoo.ingestion.IngestionApp"),
  )
  .settings(stdSettings("ingestion-app"))
  .settings(
    ThisBuild / javaOptions ++= Seq(
      "-Dlogback.configurationFile=logback-local.xml"

    ),

    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
    ),
  )

lazy val dataMigration = (project in file("data-migration"))
  .dependsOn(
    std % "compile->compile;test->test",
    memory % "compile->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
    core % "compile->compile;test->test",
    log % "compile->compile;test->compile",
    observability % "compile->compile",
    postgres % "compile->compile;test->test",
  )
  .settings(stdSettings("data-migration"))
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      // `zio-config-typesafe`,
      `zio-metrics-connectors-prometheus`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val dataMigrationApp = (project in file("data-migration-app"))
  .dependsOn(
    dataMigration % "compile->compile;test->test",
    std % "compile->compile;test->test",
    memory % "compile->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
    core % "compile->compile;test->test",
    log % "compile->compile;test->compile",
    observability % "compile->compile",
    postgres % "compile->compile;test->test",
  )
  .settings(stdSettings("data-migration-app"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(
    Docker / packageName := "youtoo-migration",
    dockerBaseImage := "eclipse-temurin:17-jre",
    dockerExposedPorts := Seq(8181),
    dockerUpdateLatest := true,
    dockerEnvVars ++= BuildHelper.getEnvVars(),
    bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=logback-production.xml"""",
    mainClass := Some("com.youtoo.migration.MigrationApp"),
  )
  .settings(
    ThisBuild / javaOptions ++= Seq(
      "-Dlogback.configurationFile=logback-local.xml"

    ),

    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
    ),
  )

lazy val job = (project in file("job"))
  .settings(stdSettings("job"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .dependsOn(
    core % "compile->compile;test->compile",
    postgres % "compile->compile;test->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
  )
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Dependencies.`open-telemetry`,
    libraryDependencies ++= Dependencies.db,
    libraryDependencies ++= Seq(
      `zio-jdbc`,
      zio,
      cats,
      `zio-prelude`,
      `zio-schema`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val mail = (project in file("mail"))
  .settings(stdSettings("mail"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .dependsOn(
    std % "compile->compile;test->test",
    sink % "compile->compile;test->test",
    core % "compile->compile;test->compile",
    postgres % "compile->compile;test->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
    job % "compile->compile;test->test",
    log % "compile->compile;test->compile",
    lock % "compile->compile;test->test",
  )
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Dependencies.`open-telemetry`,
    libraryDependencies ++= Dependencies.gmail,
    libraryDependencies ++= Dependencies.db,
    libraryDependencies ++= Seq(
      `zio-interop-cats`,
      mockito,
      `zio-jdbc`,
      zio,
      cats,
      `zio-prelude`,
      `zio-schema`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val mailApp = (project in file("mail-app"))
  .settings(stdSettings("mail-app"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .dependsOn(
    sink % "compile->compile;test->test",
    std % "compile->compile;test->test",
    memory % "compile->compile;test->test",
    core % "compile->compile;test->compile",
    postgres % "compile->compile;test->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
    job % "compile->compile;test->test",
    log % "compile->compile;test->compile",
    observability % "compile->compile",
    mail % "compile->compile;test->test",
    lock % "compile->compile;test->test",
  )
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(
    Docker / packageName := "youtoo-mail",
    dockerBaseImage := "eclipse-temurin:17-jre",
    dockerExposedPorts := Seq(8181),
    dockerUpdateLatest := true,
    dockerEnvVars ++= BuildHelper.getEnvVars(),
    bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=logback-production.xml"""",
    mainClass := Some("com.youtoo.mail.MailApp"),
  )
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
    ),
  )

lazy val sink = (project in file("sink"))
  .settings(stdSettings("sink"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .dependsOn(
    core % "compile->compile;test->compile",
    postgres % "compile->compile;test->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
    log % "compile->compile;test->compile",
  )
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Dependencies.`open-telemetry`,
    libraryDependencies ++= Dependencies.db,
    libraryDependencies ++= Seq(
      `zio-interop-cats`,
      mockito,
      `zio-jdbc`,
      zio,
      cats,
      `zio-prelude`,
      `zio-schema`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val sinkApp = (project in file("sink-app"))
  .settings(stdSettings("sink-app"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .dependsOn(
    sink % "compile->compile;test->test",
    std % "compile->compile;test->test",
    memory % "compile->compile;test->test",
    core % "compile->compile;test->compile",
    postgres % "compile->compile;test->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
    log % "compile->compile;test->compile",
    observability % "compile->compile",
  )
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(
    Docker / packageName := "youtoo-sink",
    dockerBaseImage := "eclipse-temurin:17-jre",
    dockerExposedPorts := Seq(8181),
    dockerUpdateLatest := true,
    dockerEnvVars ++= BuildHelper.getEnvVars(),
    bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=logback-production.xml"""",
    mainClass := Some("com.youtoo.mail.SinkApp"),
  )
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
    ),
  )

lazy val source = (project in file("source"))
  .settings(stdSettings("source"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .dependsOn(
    core % "compile->compile;test->compile",
    postgres % "compile->compile;test->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
    log % "compile->compile;test->compile",
  )
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Dependencies.`open-telemetry`,
    libraryDependencies ++= Dependencies.db,
    libraryDependencies ++= Seq(
      `zio-interop-cats`,
      mockito,
      `zio-jdbc`,
      zio,
      cats,
      `zio-prelude`,
      `zio-schema`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      `zio-mock`,
    ),
  )

lazy val sourceApp = (project in file("source-app"))
  .settings(stdSettings("source-app"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .dependsOn(
    sink % "compile->compile;test->test",
    std % "compile->compile;test->test",
    memory % "compile->compile;test->test",
    core % "compile->compile;test->compile",
    postgres % "compile->compile;test->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
    log % "compile->compile;test->compile",
    observability % "compile->compile",
  )
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(
    Docker / packageName := "youtoo-source",
    dockerBaseImage := "eclipse-temurin:17-jre",
    dockerExposedPorts := Seq(8181),
    dockerUpdateLatest := true,
    dockerEnvVars ++= BuildHelper.getEnvVars(),
    bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=logback-production.xml"""",
    mainClass := Some("com.youtoo.mail.SourceApp"),
  )
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
    ),
  )

lazy val jobApp = (project in file("job-app"))
   .dependsOn(
    std % "compile->compile;test->test",
    memory % "compile->compile;test->test",
    core % "compile->compile;test->compile",
    postgres % "compile->compile;test->compile;test->test",
    `cqrs-persistence-postgres` % "compile->compile;test->test",
    job % "compile->compile;test->test",
    log % "compile->compile;test->compile",
    observability % "compile->compile",
    mail % "compile->compile;test->test",
    lock % "compile->compile;test->test",

   )
   .enablePlugins(DockerPlugin, JavaAppPackaging)
   .settings(
     Docker / packageName := "youtoo-job",
     dockerBaseImage := "eclipse-temurin:17-jre",
     dockerExposedPorts := Seq(8181),
     dockerUpdateLatest := true,
     dockerEnvVars ++= BuildHelper.getEnvVars(),
     bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=logback-production.xml"""",
     mainClass := Some("com.youtoo.job.JobApp"),
   )
   .settings(stdSettings("job-app"))
   .settings(
     ThisBuild / javaOptions ++= Seq(
       "-Dlogback.configurationFile=logback-local.xml"
     ),
     testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
     libraryDependencies ++= Seq(
     ),
   )

lazy val lockApp = (project in file("lock-app"))
  .settings(stdSettings("lock-app"))
  // .settings(publishSetting(false))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("com.youtoo"))
  .dependsOn(
    std % "compile->compile;test->test",
    log % "compile->compile;test->compile",
    observability % "compile->compile",
    lock % "compile->compile;test->test",
  )
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(
    Docker / packageName := "youtoo-lock",
    dockerBaseImage := "eclipse-temurin:17-jre",
    dockerExposedPorts := Seq(8182),
    dockerUpdateLatest := true,
    dockerEnvVars ++= BuildHelper.getEnvVars(),
    bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=logback-production.xml"""",
    mainClass := Some("com.youtoo.lock.LockApp"),
  )
  .settings(
    ThisBuild / javaOptions ++= Seq(
      "-Dlogback.configurationFile=logback-local.xml"
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
    ),
  )

lazy val loadtests = (project in file("loadtests"))
  .dependsOn(ingestionApp % "compile->compile")
  .settings(stdSettings("loadtests"))
  .settings(Gatling / javaOptions := overrideDefaultJavaOptions("-Xms1G", "-Xmx4G"))
  .enablePlugins(GatlingPlugin)
  .settings(
    excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_2.13",
    libraryDependencies ++= Seq(
      `gatling-charts-highcharts`,
      `gatling-test-framework`,
    ),
  )

lazy val benchmarks = (project in file("benchmarks"))
  .dependsOn(
    std % "compile->compile;test->test",
    postgres % "compile->compile;test->test",
    kernel % "compile->compile;test->test",
    dataMigration % "compile->compile;test->test",
  )
  .settings(stdSettings("benchmarks"))
  .enablePlugins(JmhPlugin)
  .settings(
    libraryDependencies += "dev.zio" %% "zio-profiling" % "0.3.2",
    libraryDependencies += "dev.zio" %% "zio-profiling-jmh" % "0.3.2",
    libraryDependencies += compilerPlugin("dev.zio" %% "zio-profiling-tagging-plugin" % "0.3.2"),
    libraryDependencies += `scala-collection-contrib`,
  )


