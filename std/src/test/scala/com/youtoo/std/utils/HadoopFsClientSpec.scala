package com.youtoo
package std
package utils

import zio.*
import zio.test.*

object HadoopFsClientSpec extends ZIOSpecDefault {

  override def spec = suite("HadoopFsClientSpec")(
    test("write should persist content to a file") {
      val contentToWrite = "hello hadoop"

      ZIO.acquireRelease(
        ZIO.attempt(java.nio.file.Files.createTempDirectory("hadoop-test")),
      )(tmpDir => ZIO.attempt(java.nio.file.Files.deleteIfExists(tmpDir)).ignoreLogged) flatMap { tmpDir =>
        val testPath = (tmpDir.resolve("test_file.txt"))
        for {
          _ <- HadoopFsClient
            .write(contentToWrite, testPath.toString)
            .provideSomeLayer[Scope](HadoopFsClient.fs() >>> HadoopFsClient.live())
          readContent <- ZIO.attempt(java.nio.file.Files.readAllBytes(testPath)).map(bytes => new String(bytes.toArray))
        } yield assertTrue(readContent == contentToWrite)
      }

    },
  )

}
