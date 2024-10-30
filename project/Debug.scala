/**
 * Contains a list of configurations to play around with while debugging the
 * internals of CQRS.
 */
object Debug {

  /**
   * Sets the global log level for CQRS. NOTE: A clean build will be required
   * once this is changed. After which you can run the compiler in watch mode.
   *
   * Possible Values: DEBUG, ERROR, INFO, TRACE, WARN
   */
  val LogLevel = "INFO"

}
