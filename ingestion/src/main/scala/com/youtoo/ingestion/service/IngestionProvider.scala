
package ingestion
package service

import com.youtoo.ingestion.model.*

import zio.*

import com.youtoo.cqrs.service.*

trait IngestionProvider extends Provider[Ingestion] {}

object IngestionProvider {
  inline def load(id: Key): RIO[IngestionProvider, Option[Ingestion]] = ZIO.serviceWithZIO(_.load(id))

  def live(): ZLayer[IngestionService, Throwable, IngestionProvider] =
    ZLayer.fromFunction { (service: IngestionService) =>
      new IngestionProvider {
        def load(id: Key): Task[Option[Ingestion]] = service.load(Ingestion.Id(id))

      }
    }

}
