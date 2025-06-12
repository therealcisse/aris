package com.github
package aris

import com.github.aris.domain.Change
import com.github.aris.store.EventStore

import zio.*
import zio.prelude.*
import zio.schema.codec.*

transparent trait CQRS[Event, Command: [X] =>> CmdHandler[X, Event]] {
  def add(id: Key, cmd: Command): Task[Unit]
}

object CQRS {
  def apply[Event: { BinaryCodec, Tag, MetaInfo }, Command: [X] =>> CmdHandler[X, Event]](store: EventStore[Event]): CQRS[Event, Command] =
    new CQRS[Event, Command] {
      def add(id: Key, cmd: Command): Task[Unit] =
        val events = CmdHandler.applyCmd(cmd)
        ZIO.foreachDiscard(events.toChunk) { ev =>
          for {
            version <- Version.gen
            change = Change(version, ev)
            _ <- store.save(id, change)
          } yield ()
        }

    }

}
