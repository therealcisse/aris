package com.youtoo
package mail
package model

enum MailLabels {
  case All()
  case Selection(ids: List[MailLabels.LabelKey])
}

object MailLabels {
  import zio.schema.*
  import zio.prelude.*

  case class LabelInfo(id: LabelKey, name: Name, total: TotalMessages)

  object LabelInfo {
    given Schema[LabelInfo] = DeriveSchema.gen
  }

  type LabelKey = LabelKey.Type
  object LabelKey extends Newtype[String] {
    extension (a: Type) def value: String = unwrap(a)
    given Schema[LabelKey] = derive

  }

  type Name = Name.Type
  object Name extends Newtype[String] {
    given Schema[Name] = derive

  }

  type TotalMessages = TotalMessages.Type
  object TotalMessages extends Newtype[Int] {
    given Schema[TotalMessages] = derive

  }

  given Schema[MailLabels] = DeriveSchema.gen

  extension (l: MailLabels)
    def value: List[LabelKey] = l match {
      case MailLabels.All() => Nil
      case MailLabels.Selection(ids) => ids
    }

}
