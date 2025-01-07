package com.youtoo
package std
package utils

import zio.*
import zio.stream.*

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

trait HadoopFsClient {
  def write(content: String, path: String): Task[Unit]
}

object HadoopFsClient {
  case class HadoopFileSystem(fs: FileSystem) {
    export fs.{close, create}

  }

  def fs(): ZLayer[Scope, Throwable, HadoopFileSystem] =
    ZLayer.scoped(
      ZIO.acquireRelease(
        ZIO.attempt(
          HadoopFileSystem(
            FileSystem.get(new Configuration()),
          ),
        ),
      )(fs => ZIO.attempt(fs.close()).ignoreLogged),
    )

  def live(): ZLayer[HadoopFileSystem, Throwable, HadoopFsClient] =
    ZLayer.fromFunction(new HadoopFileServiceLive(_))

  def write(content: String, path: String): RIO[HadoopFsClient, Unit] =
    ZIO.serviceWithZIO[HadoopFsClient](_.write(content, path))

  case class HadoopFileServiceLive(fs: HadoopFileSystem) extends HadoopFsClient {
    def write(content: String, path: String): Task[Unit] =
      ZIO.scoped {
        for {
          outputStream <- ZIO.acquireRelease(ZIO.attempt(fs.create(new Path(path))))(os =>
            ZIO.attempt(os.close()).ignoreLogged,
          )
          _ <- ZStream.fromIterable(content.getBytes).run(ZSink.fromOutputStream(outputStream))
        } yield ()
      }
  }

}
