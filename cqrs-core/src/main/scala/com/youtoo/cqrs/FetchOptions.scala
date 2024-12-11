package com.youtoo
package cqrs

import cats.implicits.*

case class FetchOptions(offset: Option[Key], limit: Option[Long])

object FetchOptions {
  inline def apply(): FetchOptions = FetchOptions(None, None)
  inline def apply(offset: Key, limit: Long): FetchOptions = FetchOptions(offset.some, limit.some)
  inline def offset(key: Key): FetchOptions = FetchOptions(offset = key.some, limit = None)
  inline def limit(value: Long): FetchOptions = FetchOptions(offset = None, limit = value.some)
}
