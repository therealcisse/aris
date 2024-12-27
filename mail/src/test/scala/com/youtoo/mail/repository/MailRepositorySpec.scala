package com.youtoo
package mail
package repository

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.mail.model.*
import com.youtoo.cqrs.service.postgres.*
import com.youtoo.postgres.config.*
import com.youtoo.cqrs.service.*

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.jdbc.*

object MailRepositorySpec extends PgSpec, TestSupport {

  def spec: Spec[ZConnectionPool & DatabaseConfig & FlywayMigration & TestEnvironment & Scope, Any] =
    suite("MailRepositorySpec")(
      test("load mail is optimized") {
        check(mailDataIdGen) { case (id) =>
          val query = MailRepository.Queries.LOAD_MAIL(id)
          for {
            executionTime <- atomically(query.selectOne).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("load account is optimized") {
        check(mailAccountIdGen) { case (id) =>
          val query = MailRepository.Queries.LOAD_ACCOUNT(id)
          for {
            executionTime <- atomically(query.selectOne).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("load many accounts is optimized") {
        check(Gen.option(keyGen), Gen.long(100, 10_000)) { case (offset, limit) =>
          val query = MailRepository.Queries.LOAD_ACCOUNTS(FetchOptions(offset, Some(limit)))
          for {

            executionTime <- atomically(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("should update mail settings for an existing account") {
        check(mailAccountGen, mailSettingsGen) { case (account, newSettings) =>
          atomically {
            for {
              _ <- MailRepository.save(account)
              _ <- MailRepository.updateMailSettings(account.id, newSettings)
              result <- MailRepository.loadAccount(account.id)
            } yield assert(result.map(_.settings))(isSome(equalTo(newSettings)))
          }
        }
      },
      test("should save and load a mail") {
        check(mailDataGen) { mail =>
          atomically {
            for {
              _ <- MailRepository.save(mail)
              result <- MailRepository.loadMail(mail.id)
            } yield assert(result)(isSome(equalTo(mail)))
          }
        }
      },
      test("should save and load an account") {
        check(mailAccountGen) { account =>
          atomically {
            for {
              _ <- MailRepository.save(account)
              result <- MailRepository.loadAccount(account.id)
            } yield assert(result)(isSome(equalTo(account)))
          }
        }
      },
      test("should load multiple mails with loadMany") {
        check(mailDataGen, mailDataGen) { case (mail1, mail2) =>
          atomically {

            for {
              _ <- MailRepository.save(mail1)
              _ <- MailRepository.save(mail2)
              mails <- MailRepository.loadMails(FetchOptions(None, Some(10L)))
            } yield assert(mails)(contains(mail1) && contains(mail2))
          }
        }
      },
      test("should load multiple accounts with loadMany") {
        check(mailAccountGen, mailAccountGen) { case (account1, account2) =>
          atomically {

            for {
              _ <- MailRepository.save(account1)
              _ <- MailRepository.save(account2)
              accounts <- MailRepository.loadAccounts(FetchOptions(None, Some(10L)))
            } yield assert(accounts)(contains(account1) && contains(account2))
          }
        }
      },
      test("should update an existing mail") {
        check(mailDataGen) { mail =>
          atomically {

            for {
              _ <- MailRepository.save(mail)
              updatedMail = mail.copy(body = MailData.Body("updated"))
              _ <- MailRepository.save(updatedMail)
              result <- MailRepository.loadMail(mail.id)
            } yield assert(result)(isSome(equalTo(updatedMail)))
          }
        }
      },
      test("should update an existing account") {
        check(mailAccountGen) { account =>
          atomically {

            for {
              _ <- MailRepository.save(account)
              updatedAccount = account.copy(email = MailAccount.Email("updated"))
              _ <- MailRepository.save(updatedAccount)
              result <- MailRepository.loadAccount(account.id)
            } yield assert(result)(isSome(equalTo(updatedAccount)))
          }
        }
      },
      test("load should return None for non-existent mail") {
        atomically {

          for {
            id <- Random.nextUUID.map(uuid => MailData.Id(uuid.toString))
            result <- MailRepository.loadMail(id)
          } yield assert(result)(isNone)
        }
      },
      test("load should return None for non-existent account") {
        atomically {

          for {
            id <- Random.nextLong.map(l => MailAccount.Id(Key(l)))
            result <- MailRepository.loadAccount(id)
          } yield assert(result)(isNone)
        }
      },
    ).provideSomeLayerShared(
      ZLayer.make[MailRepository](
        MailRepository.live(),
        (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
      ),
    ) @@ TestAspect.withLiveClock @@ TestAspect.beforeAll {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- FlywayMigration.run(config)

      } yield ()

    } @@ TestAspect.ignore @@ TestAspect.withLiveClock @@ TestAspect.samples(2) @@ TestAspect.timeout(60.seconds)
}
