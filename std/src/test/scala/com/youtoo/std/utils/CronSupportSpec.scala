package com.youtoo
package std
package utils

import zio.*
import zio.test.*
import zio.test.Assertion.*

import com.cronutils.model.Cron

object CronSupportSpec extends ZIOSpecDefault {

  def spec = suite("CronSupportSpec")(
    test("parse method should successfully parse a valid cron string") {
      val cronString = "0 0/1 * 1/1 * ? *" // Valid Quartz cron expression
      val cron = CronSupport.parse(cronString)
      assertZIO(cron.either)(isRight(isSubtype[Cron](anything)))
    },
    test("parse method should fail on an invalid cron string") {
      val invalidCronString = "invalid cron expression"
      val cron = CronSupport.parse(invalidCronString)
      assertZIO(cron.either)(isLeft(anything))
    },
    test("should run the task at the correct time") {

      for {
        ref <- Ref.make(0)
        task = ref.set(42)

        cron <- CronSupport.parse("0/1 * * * * ?") // Every second

        scheduledTask = task.schedule(CronSupport.schedule(cron))
        fiber <- scheduledTask.fork
        _ <- TestClock.adjust(1.second)
        result <- ref.get
      } yield assert(result)(equalTo(42))
    },
  )

}
