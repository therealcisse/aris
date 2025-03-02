package com.github
package aris

case class EventProperty private[aris] (key: EventProperty.Key, value: EventProperty.PrimitiveValue)

object EventProperty {
  import zio.*
  import zio.schema.*
  import zio.prelude.*

  given Schema[EventProperty] = Schema[Map[Key, PrimitiveValue]].transform(
    _.headOption match {
      case Some((key, value: PrimitiveValue)) => EventProperty(key, value)
      case None => throw IllegalStateException("No event property found")
    },
    { case EventProperty(key, value) =>
      Map(key -> value)
    },
  )

  type Key = Key.Type

  object Key extends Newtype[String] {
    import zio.schema.*
    extension (a: Key) inline def value: String = Key.unwrap(a)

    given Schema[Key] = derive
  }

  inline def apply(inline key: String, value: String): EventProperty = EventProperty(Key(key), PrimitiveValue.S(value))
  inline def apply(inline key: String, value: Int): EventProperty = EventProperty(Key(key), PrimitiveValue.I(value))
  inline def apply(inline key: String, value: Long): EventProperty = EventProperty(Key(key), PrimitiveValue.L(value))
  inline def apply(inline key: String, value: Float): EventProperty = EventProperty(Key(key), PrimitiveValue.F(value))
  inline def apply(inline key: String, value: Double): EventProperty = EventProperty(Key(key), PrimitiveValue.D(value))
  inline def apply(inline key: String, value: Boolean): EventProperty = EventProperty(Key(key), PrimitiveValue.B(value))

  enum PrimitiveValue {
    case I(value: Int)
    case L(value: Long)
    case S(value: String)
    case F(value: Float)
    case D(value: Double)
    case B(value: Boolean)
  }

  object PrimitiveValue {
    given Schema[PrimitiveValue.I] = Schema[Int].transform(PrimitiveValue.I.apply, _.value)
    given Schema[PrimitiveValue.L] = Schema[Long].transform(PrimitiveValue.L.apply, _.value)
    given Schema[PrimitiveValue.S] = Schema[String].transform(PrimitiveValue.S.apply, _.value)
    given Schema[PrimitiveValue.F] = Schema[Float].transform(PrimitiveValue.F.apply, _.value)
    given Schema[PrimitiveValue.D] = Schema[Double].transform(PrimitiveValue.D.apply, _.value)
    given Schema[PrimitiveValue.B] = Schema[Boolean].transform(PrimitiveValue.B.apply, _.value)

    given Schema[PrimitiveValue] = Schema.Enum6(
      TypeId.parse("PrimitiveValue"),
      Schema.Case[PrimitiveValue, PrimitiveValue.I](
        "I",
        Schema[PrimitiveValue.I],
        _.asInstanceOf[PrimitiveValue.I],
        identity,
        _.isInstanceOf[PrimitiveValue.I],
      ),
      Schema.Case[PrimitiveValue, PrimitiveValue.L](
        "L",
        Schema[PrimitiveValue.L],
        _.asInstanceOf[PrimitiveValue.L],
        identity,
        _.isInstanceOf[PrimitiveValue.L],
      ),
      Schema.Case[PrimitiveValue, PrimitiveValue.S](
        "S",
        Schema[PrimitiveValue.S],
        _.asInstanceOf[PrimitiveValue.S],
        identity,
        _.isInstanceOf[PrimitiveValue.S],
      ),
      Schema.Case[PrimitiveValue, PrimitiveValue.F](
        "F",
        Schema[PrimitiveValue.F],
        _.asInstanceOf[PrimitiveValue.F],
        identity,
        _.isInstanceOf[PrimitiveValue.F],
      ),
      Schema.Case[PrimitiveValue, PrimitiveValue.D](
        "D",
        Schema[PrimitiveValue.D],
        _.asInstanceOf[PrimitiveValue.D],
        identity,
        _.isInstanceOf[PrimitiveValue.D],
      ),
      Schema.Case[PrimitiveValue, PrimitiveValue.B](
        "B",
        Schema[PrimitiveValue.B],
        _.asInstanceOf[PrimitiveValue.B],
        identity,
        _.isInstanceOf[PrimitiveValue.B],
      ),
    )

  }

}
