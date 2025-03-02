package com.github.aris;

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Enumeration;
import java.security.NoSuchAlgorithmException;

public class SnowflakeIdGenerator {
    public static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator(1);

    public static long extractTimestamp(long snowflakeId) {
        // Right-shift to remove lower bits, then add EPOCH to get the real timestamp
        long timestampPart = (snowflakeId >> TIMESTAMP_SHIFT);
        return timestampPart + EPOCH;
    }

    // Configuration for bit shifts
    private static final long EPOCH = 1288834974657L; // Custom epoch
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private long datacenterId;
    private long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("Datacenter ID out of range");
        }
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("Worker ID out of range");
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    public SnowflakeIdGenerator(long workerId) {
        this.datacenterId = createDatacenterId();
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) { // Sequence overflow, wait for the next millisecond
                timestamp = waitForNextMillisecond(timestamp);
            }
        } else {
            sequence = 0L; // Reset sequence for new timestamp
        }

        lastTimestamp = timestamp;

        // Compose the ID
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) |
            (datacenterId << DATACENTER_ID_SHIFT) |
            (workerId << WORKER_ID_SHIFT) |
            sequence;
    }

    private long waitForNextMillisecond(long timestamp) {
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    private long createDatacenterId() {
      long datacenterId;
      try {
        StringBuilder sb = new StringBuilder();
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
          NetworkInterface networkInterface = networkInterfaces.nextElement();
          byte[] mac = networkInterface.getHardwareAddress();
          if (mac != null) {
            for (byte macPort : mac) {
              sb.append(String.format("%02X", macPort));
            }
          }
        }
        datacenterId = sb.toString().hashCode();
      } catch (Exception ex) {

        try {
          // Using a fallback method that is deterministic and less likely to collide
          datacenterId = Math.abs(SecureRandom.getInstanceStrong().nextInt());
        } catch (NoSuchAlgorithmException e) {
          throw new IllegalStateException("Can not generate Datacenter Id", e);
        }

      }
      datacenterId = datacenterId & MAX_DATACENTER_ID;
      return datacenterId;
    }
}



