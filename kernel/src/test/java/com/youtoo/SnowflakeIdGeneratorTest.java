package com.youtoo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SnowflakeIdGeneratorTest {
    private SnowflakeIdGenerator generator;

    @BeforeEach
    public void setUp() {
        generator = new SnowflakeIdGenerator(1);
    }

    @Test
    public void testIdGeneration() {
        long id = generator.nextId();
        assertNotNull(id, "Generated ID should not be null");
    }

    @Test
    public void testUniqueIds() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000000; i++) {
            long id = generator.nextId();
            assertTrue(ids.add(id), "ID should be unique");
        }
    }

    @Test
    public void testTimestampExtraction() {
        long id = generator.nextId();
        long generatedTimestamp = SnowflakeIdGenerator.extractTimestamp(id);
        long currentTimestamp = System.currentTimeMillis();

        // Allow some leeway for execution time differences
        assertTrue(Math.abs(generatedTimestamp - currentTimestamp) < 10,
                   "Extracted timestamp should be close to the current time");
    }

    @Test
    public void testIdOrdering() {
        long id1 = generator.nextId();
        long id2 = generator.nextId();

        assertTrue(id2 > id1, "IDs should be sequentially ordered");
    }

    @Test
    public void testDatacenterAndWorkerIdRange() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1, -1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(32, 1)); // Out of 5-bit range
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1, 32)); // Out of 5-bit range
    }

    // @Test
    // public void testClockBackwards() {
    //     long id1 = generator.nextId();
    //
    //     // Simulate clock moving backwards by setting lastTimestamp manually
    //     generator.lastTimestamp = System.currentTimeMillis() - 1000; // Future timestamp
    //
    //     assertThrows(RuntimeException.class, generator::nextId, "Should throw exception if clock moves backwards");
    // }

}
