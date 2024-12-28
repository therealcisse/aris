package com.youtoo
package std
package utils

import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import com.cronutils.descriptor.CronDescriptor
import com.cronutils.model.time.ExecutionTime

import java.time.ZoneId
import java.time.OffsetDateTime

import scala.jdk.OptionConverters.*

import zio.*

object CronSupport {
  val parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ))
  val descriptor = CronDescriptor.instance(java.util.Locale.US)

  export descriptor.describe

  def parse(string: String): Task[Cron] =
    ZIO.attempt(parser.parse(string).validate())

  def schedule[R](cron: Cron): Schedule[R, Any, Unit] =
    val executionTime = ExecutionTime.forCron(cron)

    new Schedule[R, Any, Unit] {
      type State = Unit
      final val initial: State = ()

      final def step(now: OffsetDateTime, in: Any, state: State)(implicit
        trace: Trace,
      ): ZIO[Any, Nothing, (State, Unit, Schedule.Decision)] =
        val decision = executionTime.nextExecution(now.atZoneSameInstant(ZoneId.systemDefault())).toScala match {
          case None => Schedule.Decision.Done
          case Some(nextTime) =>
            Schedule.Decision.Continue(Schedule.Interval.after(nextTime.toOffsetDateTime()))

        }

        ZIO.succeed((state, state, decision))
    }

}
