package com.youtoo
package mail
package repository

import com.youtoo.postgres.*
import com.youtoo.mail.model.*
import com.youtoo.postgres.config.*

import zio.*
import zio.test.*
import zio.test.TestAspect.*
import zio.test.Assertion.*
import zio.jdbc.*

object MailRepositorySpec extends PgSpec, TestSupport {

  def spec: Spec[ZConnectionPool & DatabaseConfig & FlywayMigration & TestEnvironment & Scope, Any] =
    suite("MailRepositorySpec")(
      loadMailIsOptimized,
      loadAccountIsOptimized,
      shouldUpdateMailSettingsForAnExistingAccount,
      shouldSaveAndLoadAMail,
      shouldSaveAndLoadAnAccount,
      shouldLoadMultipleAccountsWithLoadMany,
      loadShouldReturnNoneForNonExistentMail,
      loadShouldReturnNoneForNonExistentAccount,
      // shouldLoadMultipleMailsWithLoadMany,
    ).provideSomeLayerShared(
      ZLayer.make[MailRepository](
        MailRepository.live(),
        (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
      ),
    ) @@ nondeterministic @@ beforeAll {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- FlywayMigration.run(config)

      } yield ()

    }

  def loadMailIsOptimized =
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
    }

  def loadAccountIsOptimized =
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
    }

  def shouldUpdateMailSettingsForAnExistingAccount =
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
    }

  def shouldSaveAndLoadAMail =
    test("should save and load a mail") {
      check(mailDataGen) { mail =>
        atomically {
          for {
            _ <- MailRepository.save(mail)
            result <- MailRepository.loadMail(mail.id)
          } yield assert(result)(isSome(equalTo(mail)))
        }
      }
    }

  def shouldSaveAndLoadAnAccount =
    test("should save and load an account") {
      check(mailAccountGen) { account =>
        atomically {
          for {
            _ <- MailRepository.save(account)
            result <- MailRepository.loadAccount(account.id)
          } yield assert(result)(isSome(equalTo(account)))
        }
      }
    }

  def shouldLoadMultipleMailsWithLoadMany =
    test("should load multiple mails with loadMany") {
      check(mailDataGen <*> mailDataGen) { case (mail1, mail2) =>
        for {
          _ <- MailRepository.save(mail2).atomically
          _ <- MailRepository.save(mail1).atomically
          mails <- MailRepository.loadMails(None, 10L).atomically
        } yield assert(mails)(contains(mail1) && contains(mail2))
      }
    }

  def shouldLoadMultipleAccountsWithLoadMany =
    test("should load multiple accounts with loadMany") {
      check(mailAccountGen, mailAccountGen) { case (account1, account2) =>
        atomically {

          for {
            _ <- MailRepository.save(account1)
            _ <- MailRepository.save(account2)
            accounts <- MailRepository.loadAccounts()
          } yield assert(accounts)(contains(account1) && contains(account2))
        }
      }
    }

  def loadShouldReturnNoneForNonExistentMail =
    test("load should return None for non-existent mail") {
      atomically {

        for {
          id <- Random.nextUUID.map(uuid => MailData.Id(uuid.toString))
          result <- MailRepository.loadMail(id)
        } yield assert(result)(isNone)
      }
    }

  def loadShouldReturnNoneForNonExistentAccount =
    test("load should return None for non-existent account") {
      atomically {

        for {
          id <- Random.nextLong.map(l => MailAccount.Id(Key(l)))
          result <- MailRepository.loadAccount(id)
        } yield assert(result)(isNone)
      }
    }
}
