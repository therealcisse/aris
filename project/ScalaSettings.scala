trait ScalaSettings {
  private val baseSettings = Seq(
    "-language:postfixOps", // Added by @tusharmath
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-encoding",
    "utf-8", // Specify character encoding used by source files.
    "-explaintypes", // Explain type errors in more detail.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-language:higherKinds", // Allow higher-kinded types
    "-unchecked", // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
    "-Xfatal-warnings", // Fail the compilation if there are any warnings.
    "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
    "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
    "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
    "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
    "-Xlint:option-implicit", // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
    // "-Xlint:unused",                 // TODO check if we still need -Wunused below

    "-Xlint:deprecation", // Enable linted deprecations.
    "-Xlint:-infer-any", // Disable infer any warning

    // "-Wunused:explicits",                        // Warn if an explicit parameter is unused.
    // "-Wunused:params",                           // Enable -Wunused:explicits,implicits.
    // "-Wunused:linted",

    "-Ybackend-parallelism",
    "8", // Enable parallelisation — change to desired number!
    "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
    "-Ycache-macro-class-loader:last-modified", // and macro definitions. This can lead to performance improvements.

    // FIXME: Disabled because of scalac bug https://github.com/scala/bug/issues/11798
    //  "-Xlint:infer-any",                 // Warn when a type argument is inferred to be `Any`.
    //  "-Ywarn-infer-any",                 // Warn when a type argument is inferred to be `Any`.
    //  "-language:experimental.macros",   // Allow macro definition (besides implementation and application). Disabled, as this will significantly change in Scala 3
    //  "-language:implicitConversions",   // Allow definition of implicit functions called views. Disabled, as it might be dropped in Scala 3. Instead use extension methods (implemented as implicit class Wrapper(val inner: Foo) extends AnyVal {}
    "-Wconf:cat=unused:info", // log unused as info
    "-opt:l:inline",
  )

  val scala3Settings: Seq[String] = Seq(
    "-source",
    "future",
    // "-noindent",
    "-Wunused:imports",
    "-Wunused:all",
    "-Wunused:unsafe-warn-patvars",
  )

}
