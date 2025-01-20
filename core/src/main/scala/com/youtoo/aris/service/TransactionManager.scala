package com.youtoo
package aris
package service

import zio.*

trait TransactionManager[Connection] {
  def apply[R, T](fa: ZIO[R & Connection, Throwable, T]): RIO[R, T]
}
