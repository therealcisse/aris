package com.youtoo
package job
package repository

import com.youtoo.job.model.*
import zio.mock.*
import zio.*

import zio.jdbc.*

object JobRepositoryMock extends Mock[JobRepository] {

  object Load extends Effect[Job.Id, Throwable, Option[Job]]
  object LoadMany extends Effect[(Option[Key], Long), Throwable, Chunk[Key]]
  object Save extends Effect[Job, Throwable, Long]

  val compose: URLayer[Proxy, JobRepository] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new JobRepository {
        def load(id: Job.Id): RIO[ZConnection, Option[Job]] =
          proxy(Load, id)

        def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]] =
          proxy(LoadMany, (offset, limit))

        def save(job: Job): RIO[ZConnection, Long] =
          proxy(Save, job)
      }
    }
}

