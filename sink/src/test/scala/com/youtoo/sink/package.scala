package com.youtoo
package sink

import com.youtoo.cqrs.Codecs.given

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import com.youtoo.sink.model.*

import zio.test.*

val keyGen: Gen[Any, Key] = Gen.fromZIO(Key.gen.orDie)
val versionGen: Gen[Any, Version] = Gen.fromZIO(Version.gen.orDie)
val timestampGen: Gen[Any, Timestamp] = Gen.fromZIO(Timestamp.gen)

val sinkIdGen: Gen[Any, SinkDefinition.Id] = keyGen.map(SinkDefinition.Id.apply)

val sinkTypeGen: Gen[Any, SinkType] =
  Gen.oneOf(
    Gen.const(SinkType.InternalTable()),
    Gen.const(
      SinkType.FileSystem(SinkType.Info.FileSystemInfo(SinkType.Info.FileSystemInfo.Path("file:///path/to/file"))),
    ),
    Gen.const(SinkType.S3Bucket(SinkType.Info.S3BucketInfo(SinkType.Info.S3BucketInfo.Location("s3://bucket/path")))),
    Gen.const(SinkType.KafkaTopic(SinkType.Info.KafkaTopicInfo(SinkType.Info.KafkaTopicInfo.Name("topic-name")))),
    Gen.const(SinkType.ExternalTable(SinkType.Info.ExternalTableInfo())),
  )

val sinkAddedGen: Gen[Any, SinkEvent] =
  (sinkIdGen <*> sinkTypeGen).map(SinkEvent.Added.apply)

val sinkDeletedGen: Gen[Any, SinkEvent] =
  sinkIdGen.map(SinkEvent.Deleted.apply)

val sinkEventGen: Gen[Any, SinkEvent] =
  Gen.oneOf(sinkAddedGen, sinkDeletedGen)

val sinkEventChangeGen: Gen[Any, Change[SinkEvent]] =
  (versionGen <*> sinkEventGen).map(Change.apply)

val addSinkGen: Gen[Any, SinkCommand.AddOrModify] =
  (sinkIdGen <*> sinkTypeGen).map(SinkCommand.AddOrModify.apply)

val deleteSinkGen: Gen[Any, SinkCommand.Delete] =
  sinkIdGen.map(SinkCommand.Delete.apply)

val sinkCommandGen: Gen[Any, SinkCommand] =
  Gen.oneOf(addSinkGen, deleteSinkGen)
