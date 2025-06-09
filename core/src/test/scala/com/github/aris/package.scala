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


val namespacesGen: Gen[Any, NonEmptyList[Namespace]] =
  (
    namespaceGen <*> Gen.listOfBounded(1, 8)(namespaceGen)
  ).map((e, s) => NonEmptyList(e, s*))


val namespaceListGen: Gen[Any, NonEmptyList[Namespace]] =
  Gen.listOfBounded(1, 5)(namespaceGen).map {
    case head :: tail => NonEmptyList(head, tail*)
    case Nil => throw IllegalStateException("Should not generate empty lists")
  }

val timeIntervalGen: Gen[Any, TimeInterval] =
  (
    timestampGen <*> timestampGen
  ).map(TimeInterval.apply)


val conditionGen: Gen[Any, PersistenceQuery.Condition] =
  for {
    namespaceOpt <- Gen.option(namespaceGen)
  } yield PersistenceQuery.condition(namespaceOpt)

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
