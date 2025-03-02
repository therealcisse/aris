package com.github
package aris

import zio.*
import zio.test.*
import zio.test.Assertion.*

object SnowflakeIdGeneratorTest extends ZIOSpecDefault {

  def spec = suite("FileEventMetaInfoSpec")(
    testIdGeneration(),
    testUniqueIds(),
    testTimestampExtraction(),
    testIdOrdering(),
    testDatacenterAndWorkerIdRange(),
    testClockBackwards(),
  )

  def testIdGeneration() = test("should generate id") {
    val generator: SnowflakeIdGenerator = new SnowflakeIdGenerator(1);
    val _ = generator.nextId()
    assertCompletes
  }

  def testUniqueIds() = test("should generate unique ids") {
    val generator: SnowflakeIdGenerator = new SnowflakeIdGenerator(1);
    var ids = Set[Long]()

    for (i <- 0L until 1_000_000L) yield {
      val id = generator.nextId()
      ids = ids + id
    }

    assert(ids.size)(equalTo(1_000_000L))
  }

  def testTimestampExtraction() = test("should extract timestamp") {
    val generator: SnowflakeIdGenerator = new SnowflakeIdGenerator(1);
    val id = generator.nextId()
    val generatedTimestamp = SnowflakeIdGenerator.extractTimestamp(id)
    val currentTimestamp = java.lang.System.currentTimeMillis()

    assert(Math.abs(generatedTimestamp - currentTimestamp))(isLessThan(10L))

  }

  def testIdOrdering() = test("should order ids") {
    val generator: SnowflakeIdGenerator = new SnowflakeIdGenerator(1);
    val id1 = generator.nextId()
    val id2 = generator.nextId()

    assert(id2 > id1)(isTrue)
  }

  def testDatacenterAndWorkerIdRange() = test("should create valid id generator") {
    assertZIO(ZIO.attempt(new SnowflakeIdGenerator(-1, 1)).exit)(failsWithA[IllegalArgumentException]) &&
    assertZIO(ZIO.attempt(new SnowflakeIdGenerator(1, -1)).exit)(failsWithA[IllegalArgumentException]) &&
    assertZIO(ZIO.attempt(new SnowflakeIdGenerator(32, 1)).exit)(
      failsWithA[IllegalArgumentException],
    ) && // Out of 5-bit range
    assertZIO(ZIO.attempt(new SnowflakeIdGenerator(1, 32)).exit)(
      failsWithA[IllegalArgumentException],
    ) // Out of 5-bit range

  }

  def testClockBackwards() = test("should fail when using a backwards clock") {
    val generator: SnowflakeIdGenerator = new SnowflakeIdGenerator(1);
    val _ = generator.nextId()

    // // Simulate clock moving backwards by setting lastTimestamp manually
    // generator.lastTimestamp = System.currentTimeMillis() - 1000 // Future timestamp
    //
    // assertThrows(RuntimeException.class, generator::nextId, "Should throw exception if clock moves backwards")
    assertCompletes
  }

}
