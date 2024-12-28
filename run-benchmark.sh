#!/bin/sh

set -xe

case "$1" in
  dataloader)
    echo "Benchmarking Dataloader"
    sbt "benchmarks/Jmh/run -prof zio.profiling.jmh.JmhZioProfiler com.youtoo.std.dataloader.DataloaderBenchmark"
    ;;
  migration)
    echo "Benchmarking DataMigration"
    sbt "benchmarks/Jmh/run -prof zio.profiling.jmh.JmhZioProfiler com.youtoo.migration.DataMigrationBenchmark"
    ;;
  *)
    echo "Invalid option: $1"
    echo "Usage: $0 {dataloader|migration}"
    exit 1
    ;;
esac

