package com.youtoo
package sink
package model

import zio.schema.{DeriveSchema, Schema}
import zio.prelude.*

enum SinkType {
  case InternalTable()
  case FileSystem(info: SinkType.Info.FileSystemInfo)
  case S3Bucket(info: SinkType.Info.S3BucketInfo)
  case KafkaTopic(info: SinkType.Info.KafkaTopicInfo)
  case ExternalTable(info: SinkType.Info.ExternalTableInfo)

}

object SinkType {
  given Schema[SinkType] = DeriveSchema.gen

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

  }

}
