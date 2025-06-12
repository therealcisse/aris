package com.github
package aris
package projection

case class Envelope[T](
  version: Version,
  event: T,
  namespace: Namespace,
  discriminator: Discriminator,
  key: Key,
)
