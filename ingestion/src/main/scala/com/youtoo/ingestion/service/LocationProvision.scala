package com.youtoo
package ingestion
package service

import com.youtoo.ingestion.model.*

import zio.*

import zio.telemetry.opentelemetry.tracing.Tracing

trait LocationProvision {
  def getFiles(path: String): Task[List[IngestionFile]]
}

object LocationProvision {
  import org.apache.hadoop.fs.{FileSystem, Path}
  import org.apache.hadoop.conf.Configuration

  import java.security.MessageDigest

  inline def getFiles(path: String): RIO[LocationProvision, List[IngestionFile]] =
    ZIO.serviceWithZIO(_.getFiles(path))

  def hadoop(): ZLayer[Any, Throwable, LocationProvision] =
    ZLayer.succeed {
      new LocationProvisionLive(Configuration())

    }

  class LocationProvisionLive(configuration: Configuration) extends LocationProvision { self =>
    def getFiles(path: String): Task[List[IngestionFile]] = ZIO.attempt {
      val fs = FileSystem.get(configuration)
      val p = new Path(path)

      val files = if (fs.exists(p) && fs.getFileStatus(p).isDirectory()) {
        val statusIterator = fs.listStatus(p).iterator
        val ingestionFiles = scala.collection.mutable.ListBuffer[Task[IngestionFile]]()

        while (statusIterator.hasNext) {
          val status = statusIterator.next()
          val filePath = status.getPath
          if (status.isFile()) {
            val metadata = IngestionFile.Metadata.File(
              size = status.getLen,
              lastModified = Timestamp(status.getModificationTime),
            )
            val sig = generateSignature(filePath.getName, metadata)
            ingestionFiles += (for {
              id <- IngestionFile.Id.gen
            } yield IngestionFile(
              id = id,
              IngestionFile.Name(filePath.getName),
              metadata,
              IngestionFile.Sig(sig),
            ))

          }

        }

        ingestionFiles.toList
      } else List.empty[Task[IngestionFile]]

      files
    } flatMap { files =>
      ZIO.collectAllPar(files)
    }

    def traced(tracing: Tracing): LocationProvision =
      new LocationProvision {
        def getFiles(path: String): Task[List[IngestionFile]] =
          self.getFiles(path) @@ tracing.aspects.span("LocationProvision.load")
      }

  }

  def generateSignature(name: String, metadata: IngestionFile.Metadata): String =
    val digest = MessageDigest.getInstance("SHA-256")
    val metadataFields = metadata match {
      case IngestionFile.Metadata.File(size, lastModified) =>
        List(size.toString, lastModified.toString).mkString("|")
    }
    val input = s"$name|$metadataFields"
    val hashBytes = digest.digest(input.getBytes("UTF-8"))
    hashBytes.map("%02x".format(_)).mkString

}
