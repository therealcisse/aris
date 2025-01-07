package com.youtoo
package mail
package service

import cats.implicits.*

import zio.*
import zio.prelude.*

import com.youtoo.sink.service.*
import com.youtoo.sink.model.*

import com.youtoo.mail.model.*

import com.youtoo.std.utils.*

case class MailSinkPool(pool: ZKeyedPool[Throwable, SinkDefinition.Id, SinkRunner[MailData]]) {
  export pool.{get, invalidate}

}

object MailSinkPool {
  object TTL extends Newtype[Duration] {
    extension (a: Type) def value: Duration = unwrap(a)
  }

  given ttl: Config[TTL.Type] = Config.duration("mailSinkPoolTTL").withDefault(15.minutes).map(TTL(_))

  object Concurrency extends Newtype[Int] {
    extension (a: Type) def value: Int = unwrap(a)
  }

  given concurrency: Config[Concurrency.Type] = Config.int("mailSinkPoolConcurrency").withDefault(4).map(Concurrency(_))

  def live(): ZLayer[Scope & SinkService & MailService & HadoopFsClient, Throwable, MailSinkPool] =
    ZLayer.scoped {

      for {
        sinkService <- ZIO.service[SinkService]
        mailService <- ZIO.service[MailService]
        fsClient <- ZIO.service[HadoopFsClient]

        poolTTL <- ZIO.config[TTL.Type]
        poolConcurrency <- ZIO.config[Concurrency.Type]

        pool <- ZKeyedPool.make(
          get = (id: SinkDefinition.Id) =>
            for {
              sink <- sinkService.load(id)

              runner <- sink.fold(ZIO.fail(new IllegalArgumentException("Sink not found"))) {
                case SinkDefinition(_, SinkType.InternalTable(), _, _) =>
                  ZIO.succeed(
                    new SinkRunner[MailData] {
                      def process(data: NonEmptyList[MailData]): Task[Unit] = mailService.saveMails(data).unit

                    },
                  )

                case SinkDefinition(_, SinkType.FileSystem(SinkType.Info.FileSystemInfo(basePath)), _, _) =>
                  ZIO.succeed(
                    new SinkRunner[MailData] {
                      def process(data: NonEmptyList[MailData]): Task[Unit] =
                        ZIO
                          .foreach(data.toChunk) { mail =>
                            val file = s"$basePath/${mail.id}"

                            fsClient.write(mail.body.value, file)
                          }
                          .unit

                    },
                  )

                case _ => ZIO.fail(new IllegalArgumentException("Sink not implemented"))
              }

            } yield runner,
          range = _ => Range(0, Math.max(1, poolConcurrency.value)),
          timeToLive = _ => poolTTL.value,
        )

      } yield MailSinkPool(pool)

    }

}
