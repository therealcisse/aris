package com.youtoo
package cqrs
package service
package postgres

import zio.*
import zio.prelude.*
import zio.test.*

val namespaceGen: Gen[Any, Namespace] =
  Gen.int.map(Namespace(_))

val discriminatorGen: Gen[Any, Discriminator] =
  Gen.alphaNumericStringBounded(5, 5).map(Discriminator(_))

val keyGen: Gen[Any, Key] = Gen.fromZIO(Key.gen.orDie)
val versionGen: Gen[Any, Version] = Gen.fromZIO(Version.gen.orDie)

val hierarchyGen: Gen[Any, Hierarchy] = Gen.oneOf(
  keyGen map { parentId => Hierarchy.Child(parentId) },
  keyGen map { grandParentId => Hierarchy.GrandChild(grandParentId) },
  (keyGen <*> keyGen) map { case (grandParentId, parentId) => Hierarchy.Descendant(grandParentId, parentId) },
)

val namespacesGen: Gen[Any, NonEmptyList[Namespace]] =
  Gen
    .setOfBounded(1, 4)(Gen.int)
    .map(s =>
      NonEmptyList.fromIterableOption(s) match {
        case None => throw IllegalArgumentException("empty")
        case Some(nes) => nes.map(Namespace.apply)
      },
    )

val eventProperyGen: Gen[Any, EventProperty] =
  (Gen.alphaNumericStringBounded(3, 8) <*> Gen.alphaNumericStringBounded(8, 36)).map(EventProperty.apply)

val eventPropertiesGen: Gen[Any, NonEmptyList[EventProperty]] =
  Gen
    .setOfBounded(1, 4)(
      eventProperyGen,
    )
    .map(s => NonEmptyList.fromIterableOption(s).getOrElse(throw IllegalArgumentException("empty")))

val namespaceListGen: Gen[Any, NonEmptyList[Namespace]] =
  Gen.listOfBounded(1, 5)(namespaceGen).map {
    case head :: tail => NonEmptyList(head, tail*)
    case Nil => throw IllegalStateException("Should not generate empty lists")
  }

val referenceGen: Gen[Any, Reference] =
  keyGen.map(Reference.apply)

val conditionGen: Gen[Any, PersistenceQuery.Condition] =
  for {
    namespaceOpt <- Gen.option(namespaceGen)
    propsOpt <- Gen.listOfBounded(0, 4)(eventProperyGen).map(NonEmptyList.fromIterableOption)
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
