package com.youtoo.cqrs
package service
package postgres

import com.youtoo.cqrs.domain.*

import zio.schema.*
import zio.schema.codec.*

import zio.jdbc.*

given [T: Schema]: JdbcDecoder[T] = JdbcDecoder.fromSchema[T]

given SqlFragment.Setter[Key] = SqlFragment.Setter[String].contramap(_.value.toString)
given SqlFragment.Setter[Version] = SqlFragment.Setter[Int].contramap(_.value)
given SqlFragment.Setter[Timestamp] = SqlFragment.Setter[Long].contramap(_.value)
