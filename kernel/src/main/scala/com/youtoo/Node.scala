package com.youtoo

import zio.*

object Node {
  inline def apply[A](f: Env => Config[A]): Config[A] =
    f(Env.load)

}
