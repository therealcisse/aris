package com.github
package aris
package projection

sealed trait RetryStrategy
object RetryStrategy {
  case object Skip extends RetryStrategy
  final case class RetryAndFail(times: Int) extends RetryStrategy
  final case class RetryAndSkip(times: Int) extends RetryStrategy
}
