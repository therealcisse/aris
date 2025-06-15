package com.github
package aris
package tenants

import aris.*

import zio.prelude.*

type TenantId = TenantId.Type

object TenantId extends Newtype[Int] {
  import zio.schema.*

  extension (id: TenantId) inline def value: Int = unwrap(id)
  extension (id: TenantId) inline def asKey: Key = Key.wrap(id.value.toLong)

  given Schema[TenantId] = derive
}
