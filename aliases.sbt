addCommandAlias("fmt", "scalafmt; Test / scalafmt; sFix;")
addCommandAlias("fmtCheck", "scalafmtCheck; Test / scalafmtCheck; sFixCheck")
addCommandAlias("sFix", "scalafix OrganizeImports; Test / scalafix OrganizeImports")
addCommandAlias("sFixCheck", "scalafix --check OrganizeImports; Test / scalafix --check OrganizeImports")

onLoadMessage := {
  import scala.Console.*

  def header(text: String): String = s"${RED}$text${RESET}"
  def item(text: String): String = s"${GREEN}> ${CYAN}$text${RESET}"
  def subItem(text: String): String = s"  ${YELLOW}> ${CYAN}$text${RESET}"

  s"""|
      |${header("_________  ________ __________  _________")}
      |${header("\\_   ___ \\ \\_____  \\\\______   \\/   _____/")}
      |${header("/    \\  \\/  /  / \\  \\|       _/\\_____  \\")}
      |${header("\\     \\____/   \\_/.  \\    |   \\/        \\")}
      |${header(" \\______  /\\_____\\ \\_/____|_  /_______  /")}
      |${header("        \\/        \\__>      \\/        \\/")}
      |
      |Useful sbt tasks:
      |${item("fmt")}: Prepares source files using scalafix and scalafmt.
      |${item("sFix")}: Fixes source files using scalafix.
      |${item("fmtCheck")}: Checks sources by applying both scalafix and scalafmt.
      |${item("sFixCheck")}: Checks sources by applying both scalafix.
      |
      |${subItem("Need help? Send us a message on discord: https://www.youtoogroup.com")}
      """.stripMargin
}
