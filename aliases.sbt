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
      |${header("""
                    _
     /\            (_)
    /  \     _ __   _   ___
   / /\ \   | '__| | | / __|
  / ____ \  | |    | | \__ \
 /_/    \_\ |_|    |_| |___/""")}
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
