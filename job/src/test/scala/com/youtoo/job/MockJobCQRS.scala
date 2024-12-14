package com.youtoo
package job

import com.youtoo.cqrs.*
import com.youtoo.job.model.*
import zio.*
import zio.mock.*

object MockJobCQRS extends Mock[JobCQRS] {

  // Define each action as an effect
  object Add extends Effect[(Key, JobCommand), Throwable, Unit]

  val compose: URLayer[Proxy, JobCQRS] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new JobCQRS {
        // Implement the `add` method to utilize the defined effect
        def add(id: Key, cmd: JobCommand): Task[Unit] =
          proxy(Add, (id, cmd))
      }
    }
}
