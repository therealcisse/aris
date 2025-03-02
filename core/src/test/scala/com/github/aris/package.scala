package com.github
package aris

import zio.*
import zio.prelude.*
import zio.test.*

val namespaceGen: Gen[Any, Namespace] =
  Gen.int.map(Namespace(_))

val discriminatorGen: Gen[Any, Discriminator] =
  Gen.alphaNumericStringBounded(5, 5).map(Discriminator(_))

val keyGen: Gen[Any, Key] = Gen.fromZIO(Key.gen)
val versionGen: Gen[Any, Version] = Gen.fromZIO(Version.gen)
val timestampGen: Gen[Any, Timestamp] = Gen.fromZIO(Timestamp.gen)

val hierarchyGen: Gen[Any, Hierarchy] = Gen.oneOf(
  keyGen map { parentId => Hierarchy.Child(parentId) },
  keyGen map { grandParentId => Hierarchy.GrandChild(grandParentId) },
  (keyGen <*> keyGen) map { case (grandParentId, parentId) => Hierarchy.Descendant(grandParentId, parentId) },
)

val namespacesGen: Gen[Any, NonEmptyList[Namespace]] =
  (
    namespaceGen <*> Gen.listOfBounded(1, 8)(namespaceGen)
  ).map((e, s) => NonEmptyList(e, s*))

val eventPropertyValueIGen: Gen[Any, EventProperty.PrimitiveValue.I] = Gen.int.map(EventProperty.PrimitiveValue.I.apply)
val eventPropertyValueLGen: Gen[Any, EventProperty.PrimitiveValue.L] =
  Gen.long.map(EventProperty.PrimitiveValue.L.apply)
val eventPropertyValueSGen: Gen[Any, EventProperty.PrimitiveValue.S] =
  Gen.alphaNumericStringBounded(8, 36).map(EventProperty.PrimitiveValue.S.apply)
val eventPropertyValueFGen: Gen[Any, EventProperty.PrimitiveValue.F] =
  Gen.float.map(EventProperty.PrimitiveValue.F.apply)
val eventPropertyValueDGen: Gen[Any, EventProperty.PrimitiveValue.D] =
  Gen.double.map(EventProperty.PrimitiveValue.D.apply)
val eventPropertyValueBGen: Gen[Any, EventProperty.PrimitiveValue.B] =
  Gen.boolean.map(EventProperty.PrimitiveValue.B.apply)

val eventPropertyValueGen: Gen[Any, EventProperty.PrimitiveValue] =
  Gen.oneOf(
    eventPropertyValueIGen,
    eventPropertyValueLGen,
    eventPropertyValueSGen,
    eventPropertyValueFGen,
    eventPropertyValueDGen,
    eventPropertyValueBGen,
  )

val eventPropertyKeyGen: Gen[Any, EventProperty.Key] =
  Gen.alphaNumericStringBounded(3, 8).map(EventProperty.Key.apply)

val eventPropertyGen: Gen[Any, EventProperty] =
  (
    Gen.alphaNumericStringBounded(3, 8) <*> eventPropertyValueGen
  ).map { case (key, value) =>
    EventProperty(EventProperty.Key(key), value)
  }

val eventPropertiesGen: Gen[Any, NonEmptyList[EventProperty]] =
  Gen
    .setOfBounded(1, 4)(
      eventPropertyGen,
    )
    .map(s => NonEmptyList.fromIterableOption(s).getOrElse(throw IllegalArgumentException("empty")))

val namespaceListGen: Gen[Any, NonEmptyList[Namespace]] =
  Gen.listOfBounded(1, 5)(namespaceGen).map {
    case head :: tail => NonEmptyList(head, tail*)
    case Nil => throw IllegalStateException("Should not generate empty lists")
  }

val timeIntervalGen: Gen[Any, TimeInterval] =
  (
    timestampGen <*> timestampGen
  ).map(TimeInterval.apply)

val referenceGen: Gen[Any, ReferenceKey] =
  keyGen.map(ReferenceKey.apply)

val conditionGen: Gen[Any, PersistenceQuery.Condition] =
  for {
    namespaceOpt <- Gen.option(namespaceGen)
    propsOpt <- Gen.listOfBounded(0, 4)(eventPropertyGen).map(NonEmptyList.fromIterableOption)
    hierarchy <- Gen.option(hierarchyGen)
    reference <- Gen.option(referenceGen)
  } yield PersistenceQuery.condition(namespaceOpt, propsOpt, hierarchy, reference)

val persistenceQueryAnyGen: Gen[Any, PersistenceQuery] =
  for {
    condition <- conditionGen
    moreConditions <- Gen.listOf(conditionGen)
  } yield PersistenceQuery.any(condition, moreConditions*)

val persistenceQueryForallGen: Gen[Any, PersistenceQuery] =
  for {
    condition <- Gen.oneOf(
      conditionGen,
      persistenceQueryAnyGen,
    )

    moreConditions <- Gen.listOf(
      Gen.oneOf(
        conditionGen,
        persistenceQueryAnyGen,
      ),
    )
  } yield PersistenceQuery.forall(condition, moreConditions*)

val persistenceQueryGen: Gen[Any, PersistenceQuery] =
  Gen.oneOf(
    persistenceQueryAnyGen,
    persistenceQueryForallGen,
    conditionGen,
  )

val orderGen: Gen[Any, FetchOptions.Order] =
  Gen.elements(
    FetchOptions.Order.asc,
    FetchOptions.Order.desc,
  )

val fetchOptionsGen: Gen[Any, FetchOptions] =
  for {
    offset <- Gen.option(keyGen)
    limit <- Gen.option(Gen.long(0, 1000))
    order <- orderGen
  } yield FetchOptions(offset, limit, order)
