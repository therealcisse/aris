import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{HeaderLicense, headerLicense}
import sbt.*
import sbt.Keys.*
import scalafix.sbt.ScalafixPlugin.autoImport.*
import xerial.sbt.Sonatype.autoImport.*

object BuildHelper extends ScalaSettings {
  val Scala3           = "3.5.1"
  val ScoverageVersion = "2.0.12"
  val JmhVersion       = "0.4.7"

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-language:postfixOps",
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
      case _            => Seq.empty
    }

  def settingsWithHeaderLicense =
    headerLicense := Some(HeaderLicense.ALv2("2024 - 2024", "YouToo Group."))

  def publishSetting(publishArtifacts: Boolean) = {
    val publishSettings = Seq(
      organization           := "com.youtoo",
      organizationName       := "cqrs",
      licenses               := Seq(),
      sonatypeCredentialHost := "oss.sonatype.org",
      sonatypeRepository     := "https://oss.sonatype.org/service/local",
      sonatypeProfileName    := "com.youtoo",
      publishTo              := sonatypePublishToBundle.value,
      sonatypeTimeoutMillis  := 300 * 60 * 1000,
      publishMavenStyle      := true,
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
    val skipSettings    = Seq(
      publish / skip  := true,
      publishArtifact := false,
    )
    if (publishArtifacts) publishSettings else publishSettings ++ skipSettings
  }

  def stdSettings(prjName: String) = Seq(
    name                           := prjName,
    ThisBuild / crossScalaVersions := Seq(Scala3),
    ThisBuild / scalaVersion       := Scala3,
    scalacOptions ++= stdOptions ++ extraOptions(scalaVersion.value),
    ThisBuild / scalafixDependencies ++=
      List(
        "com.github.vovapolu" %% "scaluzzi" % "0.1.23",
      ),
    Test / parallelExecution       := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings                := true,
    ThisBuild / javaOptions        := Seq(
      "-Dio.netty.leakDetectionLevel=paranoid",
      s"-DCQRSLogLevel=${Debug.CQRSLogLevel}",
    ),
    ThisBuild / fork               := true,
    semanticdbEnabled              := scalaVersion.value != Scala3,
    semanticdbOptions += "-P:semanticdb:synthetics:on",
    semanticdbVersion              := {
      if (scalaVersion.value == Scala3) semanticdbVersion.value
      else scalafixSemanticdb.revision
    },
  )

  def runSettings(className: String = "example.HelloWorld") = Seq(
    fork                      := true,
    Compile / run / mainClass := Option(className),
  )

  def meta = Seq(
    ThisBuild / homepage   := Some(url("https://youtoogroup.com/cqrs")),
    ThisBuild / scmInfo    :=
      Some(
        ScmInfo(url("https://github.com/therealcisse/cqrs"), "scm:git@github.com:therealcisse/cqrs.git"),
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
