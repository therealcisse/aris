package com.youtoo
package mail
package repository

import cats.implicits.*

import com.youtoo.postgres.*
import com.youtoo.mail.model.*
import com.youtoo.postgres.config.*

import zio.*
import zio.test.*
import zio.test.TestAspect.*
import zio.test.Assertion.*
import zio.jdbc.*
import zio.prelude.*

object MailRepositorySpec extends PgSpec, TestSupport {

  def spec: Spec[ZConnectionPool & DatabaseConfig & FlywayMigration & TestEnvironment & Scope, Any] =
    suite("MailRepositorySpec")(
      loadMailIsOptimized,
      loadAccountIsOptimized,
      shouldSaveAndLoadAMail,
      shouldSaveMailsAndLoadAMail,
      shouldSaveAndLoadAnAccount,
      shouldLoadMultipleAccountsWithLoadMany,
      loadShouldReturnNoneForNonExistentMail,
      loadShouldReturnNoneForNonExistentAccount,
      shouldLoadMultipleMailsWithLoadMany,
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

  def shouldSaveMailsAndLoadAMail =
    test("should save many mails and load a mail") {
      check(mailDataGen, Gen.listOf(mailDataGen)) { (mail, more) =>
        val mails = NonEmptyList(mail, more*)

        atomically {
          for {
            _ <- MailRepository.saveMails(mails)
            result <- ZIO.foreach(mails.toList)(m => MailRepository.loadMail(m.id))
            t = NonEmptyList.fromIterableOption(result.toList.mapFilter(a => a))
          } yield assert(t)(isSome(equalTo(mails)))
        }
      }
    }

  def shouldSaveAndLoadAnAccount =
    test("should save and load an account") {
      check(
        mailAccountIdGen,
        mailAccountInformationGen,
      ) { (id, info) =>
        atomically {
          for {
            _ <- MailRepository.save(id, info)
            result <- MailRepository.loadAccount(id)
          } yield assert(result)(isSome(equalTo(info.toAccount(id, MailConfig.default))))
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
      check(
        mailAccountIdGen,
        mailAccountInformationGen,
        mailAccountIdGen,
        mailAccountInformationGen,
      ) {
        case (
              id1,
              info1,
              id2,
              info2,
            ) =>
          atomically {

            for {
              _ <- MailRepository.save(id1, info1)
              _ <- MailRepository.save(id2, info2)
              accounts <- MailRepository.loadAccounts()
            } yield assert(accounts)(
              contains(id1) && contains(id2),
            )
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
