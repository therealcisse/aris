package com.youtoo
package aris

type Catalog = Catalog.TableName

object Catalog {
  import zio.prelude.*

  type TableName = TableName.Type

  object TableName extends Newtype[String] {
    extension (a: Type) inline def value: String = unwrap(a)

  }

  val Default: Catalog = Catalog.named("events")

  inline def named(inline name: String): Catalog = TableName(name)

  extension (catalog: Catalog) def tableName: String = catalog.value
}
