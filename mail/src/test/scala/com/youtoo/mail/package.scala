package com.youtoo
package mail

import zio.*
import zio.test.*
import zio.prelude.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import com.youtoo.mail.model.*
import com.youtoo.job.model.*
import com.youtoo.sink.*

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
val mailDataIdGen: Gen[Any, MailData.Id] = Gen.uuid.map(uuid => MailData.Id(uuid.toString))
val mailTokenGen: Gen[Any, MailToken] = Gen.alphaNumericStringBounded(4, 36).map(MailToken.apply)
val mailBodyGen: Gen[Any, MailData.Body] = Gen.alphaNumericStringBounded(1, 10).map(MailData.Body.apply)
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

val mailAccountNameGen: Gen[Any, MailAccount.Name] = Gen.alphaNumericStringBounded(5, 5).map(MailAccount.Name(_))
val mailAccountEmailGen: Gen[Any, MailAccount.Email] = Gen.alphaNumericStringBounded(5, 5).map(MailAccount.Email(_))

val authConfigGen: Gen[Any, AuthConfig] =
  Gen.const(
    AuthConfig(
    ),
  )

val tokenInfoGen: Gen[Any, TokenInfo] = for {
  refreshToken <- Gen.alphaNumericStringBounded(5, 5).map(TokenInfo.RefreshToken.apply)
  idToken <- Gen.option(Gen.alphaNumericStringBounded(5, 5).map(TokenInfo.IdToken.apply))
} yield TokenInfo(refreshToken, idToken)

val accountTypeGen: Gen[Any, AccountType] =
  Gen.oneOf(Gen.const(AccountType.Gmail))

val cronExpressionGen: Gen[Any, SyncConfig.CronExpression] =
  Gen.const("0/1 * * * * ?").map(SyncConfig.CronExpression.apply)

val autoSyncGen: Gen[Any, SyncConfig.AutoSync] =
  Gen.oneOf(
    cronExpressionGen.map(SyncConfig.AutoSync.enabled(_)),
    Gen.option(cronExpressionGen).map(SyncConfig.AutoSync.disabled(_)),
  )

val syncConfigGen: Gen[Any, SyncConfig] = autoSyncGen.map(SyncConfig(_))

val sinkConfigGen: Gen[Any, SinkConfig] = Gen.setOf(sinkIdGen).map(SinkConfig.Sinks(_)).map(SinkConfig(_))

val mailConfigGen: Gen[Any, MailConfig] =
  for {
    authConfig <- authConfigGen
    syncConfig <- syncConfigGen
    sinkConfig <- sinkConfigGen
  } yield MailConfig(authConfig, syncConfig, sinkConfig)

val mailConfigCommandGen: Gen[Any, MailConfigCommand] =
  Gen.oneOf(
    cronExpressionGen.map(MailConfigCommand.EnableAutoSync(_)),
    Gen.const(MailConfigCommand.DisableAutoSync()),
    authConfigGen.map(MailConfigCommand.SetAuthConfig(_)),
    (sinkIdGen).map(MailConfigCommand.LinkSink(_)),
    (sinkIdGen).map(MailConfigCommand.UnlinkSink(_)),
  )

val mailConfigEventChangeGen: Gen[Any, Change[MailConfigEvent]] =
  for {
    version <- versionGen
    event <- mailConfigEventGen
  } yield Change(version, event)

val mailConfigEventGen: Gen[Any, MailConfigEvent] =
  Gen.oneOf(
    cronExpressionGen.map(MailConfigEvent.AutoSyncEnabled(_)),
    Gen.option(cronExpressionGen).map(MailConfigEvent.AutoSyncDisabled(_)),
    authConfigGen.map(MailConfigEvent.AuthConfigSet(_)),
    sinkIdGen.map(MailConfigEvent.SinkLinked(_)),
    sinkIdGen.map(MailConfigEvent.SinkUnlinked(_)),
  )

val syncConfigEnabledGen: Gen[Any, Boolean] = Gen.boolean

val mailAccountGen: Gen[Any, MailAccount] =
  (
    mailAccountIdGen <*> accountTypeGen <*> mailAccountNameGen <*> mailAccountEmailGen <*> mailConfigGen <*> timestampGen
  ) map { case (id, accountType, name, email, settings, timestamp) =>
    MailAccount(id, accountType, name, email, settings, timestamp)
  }

val mailAccountInformationGen: Gen[Any, MailAccount.Information] =
  (
    accountTypeGen <*> mailAccountNameGen <*> mailAccountEmailGen <*> timestampGen
  ) map {
    case (
          accountType,
          name,
          email,
          timestamp,
        ) =>
      MailAccount.Information(
        accountType,
        name,
        email,
        timestamp,
      )
  }

val startSyncGen: Gen[Any, MailCommand] =
  (mailLabelsGen <*> timestampGen <*> jobIdGen) map { case (labels, timestamp, jobId) =>
    MailCommand.StartSync(labels, timestamp, jobId)
  }

val recordSyncGen: Gen[Any, MailCommand] =
  (timestampGen <*> mailDataIdGen <*> Gen.listOf(mailDataIdGen) <*> mailTokenGen <*> jobIdGen) map {
    case (timestamp, key, keys, token, jobId) =>
      MailCommand.RecordSync(timestamp, NonEmptyList(key, keys*), token, jobId)
  }

val completeSyncGen: Gen[Any, MailCommand] =
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
    keys <- Gen.listOfBounded(0, 8)(mailDataIdGen)
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

val mailEventChangeGen: Gen[Any, Change[MailEvent]] =
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

val authorizationPendingGen: Gen[Any, Authorization.Pending] = Gen.const(Authorization.Pending())
val authorizationGrantedGen: Gen[Any, Authorization.Granted] =
  (tokenInfoGen <*> timestampGen).map(Authorization.Granted.apply)
val authorizationRevokedGen: Gen[Any, Authorization.Revoked] =
  timestampGen.map(Authorization.Revoked.apply)
val authorizationGen: Gen[Any, Authorization] =
  Gen.oneOf(authorizationPendingGen, authorizationGrantedGen, authorizationRevokedGen)

val mailGen: Gen[Any, Mail] =
  (mailAccountIdGen <*> Gen.option(mailCursorGen) <*> authorizationGen).map(Mail.apply)

val unauthorizedMailGen: Gen[Any, Mail] =
  (mailAccountIdGen <*> Gen.option(mailCursorGen) <*> Gen.oneOf(authorizationPendingGen, authorizationRevokedGen))
    .map(Mail.apply)

val authorizedMailGen: Gen[Any, Mail] =
  (mailAccountIdGen <*> Gen.option(mailCursorGen) <*> authorizationGrantedGen).map(Mail.apply)

val downloadGen: Gen[Any, Download] =
  (
    mailAccountIdGen <*> Gen.option(versionGen) <*> authorizationGen
  ).map(Download.apply)

val unauthorizedDownloadGen: Gen[Any, Download] =
  (
    mailAccountIdGen <*> Gen.option(versionGen) <*> Gen.oneOf(authorizationPendingGen, authorizationRevokedGen)
  ).map(Download.apply)

val recordDownloadCommandGen: Gen[Any, DownloadCommand] =
  (versionGen <*> jobIdGen).map(DownloadCommand.RecordDownload.apply)

val downloadCommandGen: Gen[Any, DownloadCommand] =
  Gen.oneOf(recordDownloadCommandGen)

val grantAuthorizationCommandGen: Gen[Any, AuthorizationCommand] =
  (tokenInfoGen <*> timestampGen).map { case (token, timestamp) =>
    AuthorizationCommand.GrantAuthorization(token, timestamp)
  }

val revokeAuthorizationCommandGen: Gen[Any, AuthorizationCommand] =
  timestampGen.map(AuthorizationCommand.RevokeAuthorization.apply)

val authorizationCommandGen: Gen[Any, AuthorizationCommand] =
  Gen.oneOf(grantAuthorizationCommandGen, revokeAuthorizationCommandGen)

val authorizationGrantedEventGen: Gen[Any, AuthorizationEvent] =
  for {
    info <- tokenInfoGen
    timestamp <- timestampGen
  } yield AuthorizationEvent.AuthorizationGranted(info, timestamp)

val authorizationRevokedEventGen: Gen[Any, AuthorizationEvent] =
  for {
    timestamp <- timestampGen
  } yield AuthorizationEvent.AuthorizationRevoked(timestamp)

val authorizationGrantedChangeGen: Gen[Any, Change[AuthorizationEvent]] =
  (versionGen <*> authorizationGrantedEventGen).map(Change.apply)

val authorizationRevokedChangeGen: Gen[Any, Change[AuthorizationEvent]] =
  (versionGen <*> authorizationRevokedEventGen).map(Change.apply)

val authorizationEventGen: Gen[Any, AuthorizationEvent] =
  Gen.oneOf(authorizationGrantedEventGen, authorizationRevokedEventGen)

val authorizationEventChangeGen: Gen[Any, Change[AuthorizationEvent]] =
  (versionGen <*> authorizationEventGen).map { case (version, event) =>
    Change(version, event)
  }

val downloadedEventGen: Gen[Any, DownloadEvent] =
  (versionGen <*> jobIdGen).map { case (version, jobId) =>
    DownloadEvent.Downloaded(version, jobId)
  }

val downloadedChangeGen: Gen[Any, Change[DownloadEvent]] =
  (versionGen <*> downloadedEventGen).map { case (version, event) =>
    Change(version, event)
  }
