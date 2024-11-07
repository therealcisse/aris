trait ScalaSettings {

  val scala3Settings: Seq[String] = Seq(
    "-Ybackend-parallelism",
    "8", // Enable parallelisation — change to desired number!
    "-source",
    "future",
    // "-noindent",
    "-Wunused:imports",
    "-Wunused:all",
    "-Wunused:unsafe-warn-patvars",
    "-Yexplicit-nulls",
    "-Wsafe-init",
  )

}
