import com.github.sbt.git.SbtGit.git
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{headerLicense, HeaderLicense}
import sbt.*
import sbt.Keys.*
import sbtbuildinfo.*
import sbtbuildinfo.BuildInfoKeys.*
import scalafix.sbt.ScalafixPlugin.autoImport.*
import xerial.sbt.Sonatype.autoImport.*

object BuildHelper extends ScalaSettings {
  val Scala3 = "3.6.2"

  def getEnvVars(): Map[String, String] =
    Map(
      "INGESTION_SNAPSHOTS_THRESHOLD" -> sys.env.getOrElse("INGESTION_SNAPSHOTS_THRESHOLD", ""),
      "MIGRATION_SNAPSHOTS_THRESHOLD" -> sys.env.getOrElse("MIGRATION_SNAPSHOTS_THRESHOLD", ""),
      "DATABASE_URL" -> sys.env.getOrElse("DATABASE_URL", ""),
      "DATABASE_USERNAME" -> sys.env.getOrElse("DATABASE_USERNAME", ""),
      "DATABASE_PASSWORD" -> sys.env.getOrElse("DATABASE_PASSWORD", ""),
    )

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-language:postfixOps",
    "-Xmax-inlines",
    "4096",
  ) ++ {
    if (sys.env.contains("CI")) {
      Seq("-Xfatal-warnings")
    } else {
      Nil // to enable Scalafix locally
    }
  }

  def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _)) => scala3Settings
      case _ => Seq.empty
    }

  def settingsWithHeaderLicense =
    headerLicense := Some(HeaderLicense.ALv2("2023 - 2024", "YouToo Group."))

  def publishSetting(publishArtifacts: Boolean) = {
    val publishSettings = Seq(
      organization := "com.github",
      organizationName := "youtoo",
      licenses := Seq(),
      sonatypeCredentialHost := "oss.sonatype.org",
      sonatypeRepository := "https://oss.sonatype.org/service/local",
      sonatypeProfileName := "com.github",
      publishTo := sonatypePublishToBundle.value,
      sonatypeTimeoutMillis := 300 * 60 * 1000,
      publishMavenStyle := true,
      credentials ++=
        (for {
          username <- Option(System.getenv().get("SONATYPE_USERNAME"))
          password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
        } yield Credentials(
          "Sonatype Nexus Repository Manager",
          "oss.sonatype.org",
          username,
          password,
        )).toSeq,
    )
    val skipSettings = Seq(
      publish / skip := true,
      publishArtifact := false,
    )
    if (publishArtifacts) publishSettings else publishSettings ++ skipSettings
  }

  def buildInfoSettings(packageName: String) =
    Seq(
      // BuildInfoOption.ConstantValue required to disable assertions in FiberRuntime!
      buildInfoOptions += BuildInfoOption.ConstantValue,
      buildInfoOptions += BuildInfoOption.ToJson,
      buildInfoOptions += BuildInfoOption.ToMap,
      buildInfoOptions += BuildInfoOption.BuildTime,
      buildInfoObject := "ProjectInfo",
      buildInfoKeys := Seq[BuildInfoKey](
        organization,
        moduleName,
        name,
        version,
        scalaVersion,
        sbtVersion,
        isSnapshot,
      ),
      buildInfoPackage := packageName,
    )

  // Keep this consistent with the version in .core-tests/shared/src/test/scala/REPLSpec.scala
  val replSettings = makeReplSettings {
    """|import zio.*
       |import zio.jdbc.*
       |import zio.schema.*
       |
       |extension[A](io: ZIO[Any, Any, A])
       |  def r: A =
       |    Unsafe.unsafe { implicit unsafe =>
       |      Runtime.default.unsafe.run(io).getOrThrowFiberFailure()
       |    }
       |
       |import com.github.*
       |
       |import zio.schema.codec.*
    """.stripMargin
  }

  def makeReplSettings(initialCommandsStr: String) = Seq(
    // In the repl most warnings are useless or worse.
    // This is intentionally := as it's more direct to enumerate the few
    // options we do want than to try to subtract off the ones we don't.
    // One of -Ydelambdafy:inline or -Yrepl-class-based must be given to
    // avoid deadlocking on parallel operations, see
    //   https://issues.scala-lang.org/browse/SI-9076
    // Compile / console / scalacOptions ++= Seq(
    //   "-language:higherKinds",
    //   "-language:existentials",
    //   "-Xsource:2.13",
    //   "-Yrepl-class-based",
    // ),
    Compile / console / initialCommands := initialCommandsStr,
  )
  def stdSettings(prjName: String) = Seq(
    name := prjName,
    ThisBuild / scalaVersion := Scala3,
    Compile / packageSrc / publishArtifact := false,
    Compile / packageDoc / publishArtifact := false,
    scalacOptions ++= stdOptions ++ extraOptions(scalaVersion.value),
    ThisBuild / scalafixDependencies ++=
      List(
        "com.github.vovapolu" %% "scaluzzi" % "0.1.23",
      ),
    Test / parallelExecution := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings := true,
    ThisBuild / Test / javaOptions ++= Seq(
      "-Dlogback.configurationFile=logback-test.xml",
    ),
    ThisBuild / javaOptions ++= Seq(
      s"-DARIS_LOG_LEVEL=${sys.env.getOrElse("ARIS_LOG_LEVEL", Debug.LogLevel)}",
    ),
    Compile / run / javaOptions ++= Seq(
      "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
      "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
      "--add-exports=java.base/sun.security.x509=ALL-UNNAMED",
      "--add-exports=java.base/sun.security.util=ALL-UNNAMED",
    ),
    Test / javaOptions += "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
    ThisBuild / fork := true,
    semanticdbEnabled := scalaVersion.value != Scala3,
    semanticdbOptions += "-P:semanticdb:synthetics:on",
  )

  def runSettings(className: String = "example.HelloWorld") = Seq(
    fork := true,
    Compile / run / mainClass := Option(className),
  )

  def meta = Seq(
    ThisBuild / homepage := Some(url("https://youtoogroup.com/aris")),
    ThisBuild / scmInfo :=
      Some(
        ScmInfo(url("https://github.com/therealcisse/aris"), "scm:git@github.com:therealcisse/aris.git"),
      ),
    ThisBuild / developers := List(
      Developer(
        "therealcisse",
        "Amadou Cisse",
        "amadou.cisse@youtoogroup.com",
        url("https://www.youtoogroup.com"),
      ),
    ),
  )

}
