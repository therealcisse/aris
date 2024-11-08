package com.youtoo
package ingestion

import zio.*

import com.youtoo.ingestion.model.*

trait IngestionApp {
  type Environment

  def loadIds(ids: List[Key]): ZIO[Environment, Throwable, List[Ingestion]]
  def loadAll(offset: Option[Key], limit: Long): ZIO[Environment, Throwable, List[Key]]
  def load(id: Key): ZIO[Environment, Throwable, Option[Ingestion]]
  def addCmd(cmd: IngestionCommand): ZIO[Environment, Throwable, Unit]
  def addIngestion(): ZIO[Environment, Throwable, Key]
}
