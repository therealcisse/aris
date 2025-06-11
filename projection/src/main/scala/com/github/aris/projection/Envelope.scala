package com.github
package aris
package projection

import com.github.aris.domain.*

case class Envelope[T](
  version: Version,
  event: T,
  namespace: Namespace,
  discriminator: Discriminator,
  key: Key,
)
