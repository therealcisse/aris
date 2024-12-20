package com.youtoo
package mail
package model

import zio.prelude.*

enum MailLabels {
  case All()
  case Selection(ids: NonEmptyList[MailLabels.LabelKey])
}

object MailLabels {
  import zio.schema.*

  case class LabelInfo(id: LabelKey, name: Name, total: TotalMessages)

  object LabelInfo {
    given Schema[LabelInfo] = DeriveSchema.gen
  }

  type LabelKey = LabelKey.Type
  object LabelKey extends Newtype[String] {
    extension (a: Type) def value: String = unwrap(a)
    given Schema[Type] = derive

  }

  type Name = Name.Type
  object Name extends Newtype[String] {
    given Schema[Type] = derive

  }

  given Schema[MailLabels] = DeriveSchema.gen

  extension (l: MailLabels)
    def value: List[LabelKey] = l match {
      case MailLabels.All() => Nil
      case MailLabels.Selection(ids) => ids.toList
    }

}
