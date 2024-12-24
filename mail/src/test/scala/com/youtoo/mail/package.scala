package com.youtoo
package mail

import zio.*
import zio.test.*
import zio.prelude.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import com.youtoo.mail.model.*
import com.youtoo.job.model.*

import com.youtoo.cqrs.Codecs.given

val keyGen: Gen[Any, Key] = Gen.fromZIO(Key.gen.orDie)
val versionGen: Gen[Any, Version] = Gen.fromZIO(Version.gen.orDie)
val timestampGen: Gen[Any, Timestamp] = Gen.fromZIO(Timestamp.gen)
val jobIdGen: Gen[Any, Job.Id] = Gen.fromZIO(Job.Id.gen.orDie)

val mailAccountIdGen: Gen[Any, MailAccount.Id] = Gen.fromZIO(MailAccount.Id.gen.orDie)
val mailLabelKeyGen: Gen[Any, MailLabels.LabelKey] =
  Gen.alphaNumericStringBounded(4, 36).map(MailLabels.LabelKey.apply(_))
val mailLabelsGen: Gen[Any, MailLabels] =
  Gen.oneOf(
    Gen.const(MailLabels.All()),
    (mailLabelKeyGen <*> Gen.listOf(mailLabelKeyGen)).map { case (key, keys) =>
      MailLabels.Selection(NonEmptyList(key, keys*))
    },
  )
val mailDataIdGen: Gen[Any, MailData.Id] = Gen.alphaNumericStringBounded(4, 36).map(MailData.Id.apply)
val mailTokenGen: Gen[Any, MailToken] = Gen.alphaNumericStringBounded(4, 36).map(MailToken.apply)
val mailBodyGen: Gen[Any, MailData.Body] = Gen.alphaNumericStringBounded(10, 10000).map(MailData.Body.apply)
val totalMessagesGen: Gen[Any, TotalMessages] = Gen.int(4, 16).map(TotalMessages.apply)
val mailCursorGen: Gen[Any, Cursor] = (
  timestampGen <*> mailTokenGen <*> totalMessagesGen <*> Gen.boolean
).map(Cursor.apply)

val internalDateGen: Gen[Any, InternalDate] = timestampGen.map(InternalDate.apply)
val mailDataGen: Gen[Any, MailData] =
  (
    mailDataIdGen <*> mailBodyGen <*> mailAccountIdGen <*> internalDateGen <*> timestampGen
  ).map { case (id, body, accountId, internalDate, timestamp) =>
    MailData(id, body, accountId, internalDate, timestamp)
  }

val mailAccountNameGen: Gen[Any, MailAccount.Name] = Gen.alphaNumericStringBounded(5, 50).map(MailAccount.Name(_))
val mailAccountEmailGen: Gen[Any, MailAccount.Email] = Gen.alphaNumericStringBounded(5, 50).map(MailAccount.Email(_))

val authConfigGen: Gen[Any, AuthConfig] =
  (Gen.alphaNumericStringBounded(5, 50).map(AuthConfig.ClientId.apply) <*> Gen
    .alphaNumericStringBounded(5, 50)
    .map(AuthConfig.ClientSecret.apply)).map(AuthConfig.apply)

val syncConfigCronGen: Gen[Any, SyncConfig.CronExpression] =
  Gen.const("0/1 * * * * ?").map(SyncConfig.CronExpression.apply)
val syncConfigEnabledGen: Gen[Any, Boolean] = Gen.boolean
val syncConfigGen: Gen[Any, SyncConfig] =
  (syncConfigCronGen <*> syncConfigEnabledGen).map(SyncConfig.apply)

val mailSettingsGen: Gen[Any, MailSettings] =
  (authConfigGen <*> syncConfigGen).map(MailSettings.apply)

val mailAccountGen: Gen[Any, MailAccount] =
  (mailAccountIdGen <*> mailAccountNameGen <*> mailAccountEmailGen <*> mailSettingsGen <*> timestampGen) map {
    case (id, name, email, settings, timestamp) =>
      MailAccount(id, name, email, settings, timestamp)
  }

val startSyncGen: Gen[Any, MailCommand.StartSync] =
  (mailLabelsGen <*> timestampGen <*> jobIdGen) map { case (labels, timestamp, jobId) =>
    MailCommand.StartSync(labels, timestamp, jobId)
  }

val recordSyncGen: Gen[Any, MailCommand.RecordSync] =
  (timestampGen <*> mailDataIdGen <*> Gen.listOf(mailDataIdGen) <*> mailTokenGen <*> jobIdGen) map {
    case (timestamp, key, keys, token, jobId) =>
      MailCommand.RecordSync(timestamp, NonEmptyList(key, keys*), token, jobId)
  }

val completeSyncGen: Gen[Any, MailCommand.CompleteSync] =
  (timestampGen <*> jobIdGen).map(MailCommand.CompleteSync.apply)

val mailCommandGen: Gen[Any, MailCommand] =
  Gen.oneOf(
    startSyncGen,
    recordSyncGen,
    completeSyncGen,
  )

val syncStartedGen: Gen[Any, MailEvent] =
  for {
    labels <- mailLabelsGen
    timestamp <- timestampGen
    jobId <- jobIdGen
  } yield MailEvent.SyncStarted(labels, timestamp, jobId)

val mailSyncedGen: Gen[Any, MailEvent] =
  for {
    timestamp <- timestampGen
    key <- mailDataIdGen
    keys <- Gen.listOf(mailDataIdGen)
    token <- mailTokenGen
    jobId <- jobIdGen
  } yield MailEvent.MailSynced(timestamp = timestamp, mailKeys = NonEmptyList(key, keys*), token = token, jobId = jobId)

val syncCompletedGen: Gen[Any, MailEvent] =
  for {
    timestamp <- timestampGen
    jobId <- jobIdGen
  } yield MailEvent.SyncCompleted(timestamp = timestamp, jobId = jobId)

val mailEventGen: Gen[Any, MailEvent] =
  Gen.oneOf(
    syncStartedGen,
    mailSyncedGen,
    syncCompletedGen,
  )

val changeEventGen: Gen[Any, Change[MailEvent]] =
  (versionGen <*> mailEventGen).map(Change.apply)

val syncStartedChangeGen: Gen[Any, Change[MailEvent]] =
  (versionGen <*> syncStartedGen).map(Change.apply)

val syncCompletedChangeGen: Gen[Any, Change[MailEvent]] =
  (versionGen <*> syncCompletedGen).map(Change.apply)

val mailSyncedChangeGen: Gen[Any, Change[MailEvent]] =
  (versionGen <*> mailSyncedGen).map(Change.apply)

def validMailEventSequenceGen(isCompleted: Boolean = true): Gen[Any, NonEmptyList[Change[MailEvent]]] =
  for {
    startEvent <- syncStartedGen
    version <- versionGen
    startChange = Change(version, startEvent)
    otherEvents <- Gen.listOf(mailSyncedGen)
    progressChanges <- Gen.fromZIO {
      ZIO.foreach(otherEvents) { event =>
        for {
          version <- Version.gen.orDie
        } yield Change(version, event)
      }
    }
    done <- syncCompletedGen
    version <- versionGen
    changes = progressChanges ::: (if isCompleted then (Change(version, done) :: Nil) else Nil)
  } yield NonEmptyList(startChange, changes*)
