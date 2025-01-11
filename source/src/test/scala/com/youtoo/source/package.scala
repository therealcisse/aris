package com.youtoo
package source

import com.youtoo.cqrs.Codecs.given

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import com.youtoo.source.model.*

import zio.test.*

val keyGen: Gen[Any, Key] = Gen.fromZIO(Key.gen.orDie)
val versionGen: Gen[Any, Version] = Gen.fromZIO(Version.gen.orDie)
val timestampGen: Gen[Any, Timestamp] = Gen.fromZIO(Timestamp.gen)

val sourceIdGen: Gen[Any, SourceDefinition.Id] = keyGen.map(SourceDefinition.Id.apply)

val sourceTypeGen: Gen[Any, SourceType] =
  Gen.oneOf(
    Gen.const(
      SourceType.FileSystem(SourceType.Info.FileSystemInfo(SourceType.Info.FileSystemInfo.Path("file:///path/to/file"))),
    ),
    Gen.const(
      SourceType.S3Bucket(SourceType.Info.S3BucketInfo(SourceType.Info.S3BucketInfo.Location("s3://bucket/path"))),
    ),
    Gen.const(SourceType.KafkaTopic(SourceType.Info.KafkaTopicInfo(SourceType.Info.KafkaTopicInfo.Name("topic-name")))),
    Gen.const(SourceType.ExternalTable(SourceType.Info.ExternalTableInfo())),
    Gen.const(SourceType.Grpc(SourceType.Info.GrpcInfo())),
  )

val sourceAddedGen: Gen[Any, SourceEvent] =
  (sourceIdGen <*> sourceTypeGen).map(SourceEvent.Added.apply)

val sourceDeletedGen: Gen[Any, SourceEvent] =
  sourceIdGen.map(SourceEvent.Deleted.apply)

val sourceEventGen: Gen[Any, SourceEvent] =
  Gen.oneOf(sourceAddedGen, sourceDeletedGen)

val sourceEventChangeGen: Gen[Any, Change[SourceEvent]] =
  (versionGen <*> sourceEventGen).map(Change.apply)

val addSourceGen: Gen[Any, SourceCommand.AddOrModify] =
  (sourceIdGen <*> sourceTypeGen).map(SourceCommand.AddOrModify.apply)

val deleteSourceGen: Gen[Any, SourceCommand.Delete] =
  sourceIdGen.map(SourceCommand.Delete.apply)

val sourceCommandGen: Gen[Any, SourceCommand] =
  Gen.oneOf(addSourceGen, deleteSourceGen)
