import BuildHelper.*
import Dependencies.*

// import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.*
// import com.typesafe.sbt.packager.docker.*

import sbt.Keys.*

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
    dataMigration,
  )

inThisBuild(
  replSettings,
)

lazy val root = (project in file("."))
  .settings(stdSettings("cqrs-root"))
  // .settings(publishSetting(false))
  .settings(meta)
  .aggregate(aggregatedProjects *)

lazy val core = (project in file("cqrs-core"))
  .settings(stdSettings("cqrs-core"))
  // .settings(publishSetting(false))
  .settings(buildInfoSettings("cqrs"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      // pprint,
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
  // .settings(publishSetting(false))
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
  // .settings(publishSetting(false))
  // .settings(
  //   dockerBaseImage := "openjdk:11",
  //   dockerExposedPorts := Seq(8181, 10001),
  //
  //   dockerEnvVars := Map(
  //     "DATABASE_URL" -> sys.env.getOrElse("DATABASE_URL", ""),
  //     "DATABASE_USERNAME" -> sys.env.getOrElse("DATABASE_USERNAME", ""),
  //     "DATABASE_PASSWORD" -> sys.env.getOrElse("DATABASE_PASSWORD", ""),
  //     // "JAVA_OPTS" -> s"-agentpath:/usr/local/YourKit-JavaProfiler-2024.9/bin/${sys.props.getOrElse("ARCH", "linux-arm-64")}/libyjpagent.so=port=10001,listen=all -Xms2G -Xmx2G -server",
  //   ),
  //
  //   dockerBuildxPlatforms := Seq("linux/arm64/v8", "linux/amd64"),
  //
  //   Docker / dockerCommands ++= Seq(
  //     Cmd("USER", "root"),
  //     Cmd("RUN", "wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2024.9-docker.zip -P /tmp/"),
  //     Cmd("RUN", "unzip /tmp/YourKit-JavaProfiler-2024.9-docker.zip -d /usr/local"),
  //     Cmd("RUN", "rm /tmp/YourKit-JavaProfiler-2024.9-docker.zip"),
  //   ),
  //
  //   dockerEntrypoint := Seq("./bin/cqrs-example-ingestion"),
  //
  //   bashScriptExtraDefines += s"""addJava "-agentpath:/usr/local/YourKit-JavaProfiler-2024.9/bin/${sys.props.getOrElse("ARCH", "linux-arm-64")}/libyjpagent.so=port=10001,listen=all,sampling -Xms2G -Xmx4G -server"""",
  // )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
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

lazy val dataMigration = (project in file("data-migration"))
  .settings(stdSettings("data-migration"))
  // .settings(publishSetting(false))
  // .settings(
  //   dockerBaseImage := "openjdk:11",
  //   dockerExposedPorts := Seq(8181, 10001),
  //
  //   dockerEnvVars := Map(
  //     "DATABASE_URL" -> sys.env.getOrElse("DATABASE_URL", ""),
  //     "DATABASE_USERNAME" -> sys.env.getOrElse("DATABASE_USERNAME", ""),
  //     "DATABASE_PASSWORD" -> sys.env.getOrElse("DATABASE_PASSWORD", ""),
  //     // "JAVA_OPTS" -> s"-agentpath:/usr/local/YourKit-JavaProfiler-2024.9/bin/${sys.props.getOrElse("ARCH", "linux-arm-64")}/libyjpagent.so=port=10001,listen=all -Xms2G -Xmx2G -server",
  //   ),
  //
  //   dockerBuildxPlatforms := Seq("linux/arm64/v8", "linux/amd64"),
  //
  //   Docker / dockerCommands ++= Seq(
  //     Cmd("USER", "root"),
  //     Cmd("RUN", "wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2024.9-docker.zip -P /tmp/"),
  //     Cmd("RUN", "unzip /tmp/YourKit-JavaProfiler-2024.9-docker.zip -d /usr/local"),
  //     Cmd("RUN", "rm /tmp/YourKit-JavaProfiler-2024.9-docker.zip"),
  //   ),
  //
  //   dockerEntrypoint := Seq("./bin/cqrs-example-ingestion"),
  //
  //   bashScriptExtraDefines += s"""addJava "-agentpath:/usr/local/YourKit-JavaProfiler-2024.9/bin/${sys.props.getOrElse("ARCH", "linux-arm-64")}/libyjpagent.so=port=10001,listen=all,sampling -Xms2G -Xmx4G -server"""",
  // )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
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
  // .settings(publishSetting(false))
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
