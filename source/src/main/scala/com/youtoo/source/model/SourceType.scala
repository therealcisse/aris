package com.youtoo
package source
package model

import zio.schema.{DeriveSchema, Schema}
import zio.prelude.*

enum SourceType {
  case FileSystem(info: SourceType.Info.FileSystemInfo)
  case S3Bucket(info: SourceType.Info.S3BucketInfo)
  case KafkaTopic(info: SourceType.Info.KafkaTopicInfo)
  case ExternalTable(info: SourceType.Info.ExternalTableInfo)
  case Grpc(info: SourceType.Info.GrpcInfo)

}

object SourceType {
  given Schema[SourceType] = DeriveSchema.gen

  object Info {
    case class FileSystemInfo(path: FileSystemInfo.Path)

    object FileSystemInfo {
      type Path = Path.Type

      object Path extends Newtype[String] {
        given Schema[Type] = derive
      }

      given Schema[FileSystemInfo] = DeriveSchema.gen
    }

    case class S3BucketInfo(location: S3BucketInfo.Location)

    object S3BucketInfo {
      type Location = Location.Type

      object Location extends Newtype[String] {
        given Schema[Location] = derive

      }

      given Schema[S3BucketInfo] = DeriveSchema.gen
    }

    case class KafkaTopicInfo(name: KafkaTopicInfo.Name)

    object KafkaTopicInfo {
      type Name = Name.Type

      object Name extends Newtype[String] {
        given Schema[Type] = derive

      }

      given Schema[KafkaTopicInfo] = DeriveSchema.gen
    }

    case class ExternalTableInfo()

    object ExternalTableInfo {
      given Schema[ExternalTableInfo] = DeriveSchema.gen
    }

    case class GrpcInfo()

    object GrpcInfo {
      given Schema[GrpcInfo] = DeriveSchema.gen
    }

  }

}
