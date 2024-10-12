package com.youtoo.cqrs
package domain

case class Aggregate(id: Key, version: Version)

object Aggregate {
  import zio.schema.*

  given Schema[Aggregate] =
    Schema.CaseClass2[Key, Version, Aggregate](
      id0 = TypeId.fromTypeName("Aggregate"),
      field01 = Schema.Field(
        name0 = "id",
        schema0 = Schema[Key],
        get0 = _.id,
        set0 = (c, x) => c.copy(id = x),
      ),
      field02 = Schema.Field(
        name0 = "version",
        schema0 = Schema[Version],
        get0 = _.version,
        set0 = (c, version) => c.copy(version = version),
      ),
      construct0 = (id, version) => Aggregate(id, version),
    )
}
