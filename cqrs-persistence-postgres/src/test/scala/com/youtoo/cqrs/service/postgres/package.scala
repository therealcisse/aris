package com.youtoo
package cqrs
package service
package postgres

import zio.*
import zio.prelude.*
import zio.test.*

val keyGen: Gen[Any, Key] = Gen.long.map(Key.apply)

val genNamespace: Gen[Any, Namespace] = Gen.int.map(Namespace.apply)

val genEventProperty: Gen[Any, EventProperty] =
  (Gen.alphaNumericStringBounded(4, 32) <*> Gen.alphaNumericString).map(EventProperty.apply)

val genNamespaceList: Gen[Any, NonEmptyList[Namespace]] =
  Gen.listOfBounded(1, 5)(genNamespace).map {
    case head :: tail => NonEmptyList(head, tail*)
    case Nil => throw IllegalStateException("Should not generate empty lists")
  }

val genHierarchy: Gen[Any, Hierarchy] =
  keyGen.flatMap { key =>
    Gen.oneOf(
      Gen.const(Hierarchy.Child(key)),
      Gen.const(Hierarchy.GrandChild(key)),
      (keyGen <*> keyGen).map { case (gk, pk) => Hierarchy.Descendant(gk, pk) },
    )
  }

val genReference: Gen[Any, Reference] =
  keyGen.map(Reference.apply)

val genCondition: Gen[Any, PersistenceQuery.Condition] =
  for {
    namespaceOpt <- Gen.option(genNamespace)
    propsOpt <- Gen.listOfBounded(0, 16)(genEventProperty).map(NonEmptyList.fromIterableOption)
    hierarchy <- Gen.option(genHierarchy)
    reference <- Gen.option(genReference)
  } yield PersistenceQuery.condition(namespaceOpt, propsOpt, hierarchy, reference)

val genPersistenceQueryAny: Gen[Any, PersistenceQuery] =
  for {
    condition <- genCondition
    moreConditions <- Gen.listOf(genCondition)
  } yield PersistenceQuery.any(condition, moreConditions*)

val genPersistenceQueryForall: Gen[Any, PersistenceQuery] =
  for {
    condition <- Gen.oneOf(
      genCondition,
      genPersistenceQueryAny,
    )

    moreConditions <- Gen.listOf(
      Gen.oneOf(
        genCondition,
        genPersistenceQueryAny,
      ),
    )
  } yield PersistenceQuery.forall(condition, moreConditions*)

val genPersistenceQuery: Gen[Any, PersistenceQuery] =
  Gen.oneOf(
    genPersistenceQueryAny,
    genPersistenceQueryForall,
    genCondition,
  )

val genFetchOptions: Gen[Any, FetchOptions] =
  for {
    offset <- Gen.option(keyGen)
    limit <- Gen.option(Gen.long)
  } yield FetchOptions(offset, limit)
