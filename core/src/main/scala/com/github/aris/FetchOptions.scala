package com.github
package aris

import cats.implicits.*

case class FetchOptions(offset: Option[Key], limit: Option[Long], order: FetchOptions.Order)

object FetchOptions {
  inline def apply(): FetchOptions = FetchOptions(None, None, Order.asc)
  inline def apply(offset: Key, limit: Long, order: FetchOptions.Order = Order.asc): FetchOptions =
    FetchOptions(offset.some, limit.some, order)

  inline def offset(key: Key): FetchOptions = FetchOptions(offset = key.some, limit = None, order = Order.asc)
  inline def limit(value: Long): FetchOptions = FetchOptions(offset = None, limit = value.some, order = Order.asc)
  inline def asc(): FetchOptions = FetchOptions(offset = None, limit = None, order = Order.asc)
  inline def desc(): FetchOptions = FetchOptions(offset = None, limit = None, order = Order.desc)

  extension (options: FetchOptions) inline def offset(key: Key): FetchOptions = options.copy(offset = Some(key))
  extension (options: FetchOptions) inline def limit(value: Long): FetchOptions = options.copy(limit = Some(value))
  extension (options: FetchOptions) inline def asc(): FetchOptions = options.copy(order = Order.asc)
  extension (options: FetchOptions) inline def desc(): FetchOptions = options.copy(order = Order.desc)

  enum Order {
    case asc, desc

  }
}
