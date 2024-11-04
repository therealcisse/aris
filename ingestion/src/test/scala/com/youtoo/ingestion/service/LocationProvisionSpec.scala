package com.youtoo
package ingestion
package service

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import com.youtoo.ingestion.model.*

object LocationProvisionSpec extends ZIOSpecDefault {

  def spec = suite("LocationProvision Suite")(
    test("getFiles should return file list from given path") {
      check(timestampGen, timestampGen) { (t0, t1) =>
        val testPath = "/test-path"
        val testFiles = List(
          IngestionFile(
            IngestionFile.Id(1L),
            IngestionFile.Name("file1"),
            IngestionFile.Metadata.File(100L, t0),
            IngestionFile.Sig("sig1"),
          ),
          IngestionFile(
            IngestionFile.Id(2L),
            IngestionFile.Name("file2"),
            IngestionFile.Metadata.File(200L, t1),
            IngestionFile.Sig("sig2"),
          ),
        )

        val mockLayer = MockLocationProvision.GetFiles(equalTo(testPath), value(testFiles))

        val result = LocationProvision.getFiles(testPath).provideLayer(mockLayer)
        assertZIO(result)(equalTo(testFiles))
      }
    },
    test("generateSignature should return consistent hash for given inputs") {
      val name = "testFile.txt"
      val metadata = IngestionFile.Metadata.File(
        size = 12345L,
        lastModified = Timestamp(0L),
      )
      val expectedSignature = "e94fc7353e78abc809fe61d103b23b6620810d163e17471abdcfd2e239e3a97e"

      val result = LocationProvision.generateSignature(name, metadata)

      assert(result)(equalTo(expectedSignature))
    },
  )

}
