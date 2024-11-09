#!/bin/sh

COMMIT_SHA=$(git rev-parse --short HEAD)
TAG="youtoo-profiling:$COMMIT_SHA"

if [[ $(uname -m) == 'arm64' ]]; then
    export ARCH="linux-arm-64"
else
    export ARCH="linux-x86-64"
fi

if [ ! -e "/var/run/docker.sock" ]; then
    echo "'/var/run/docker.sock' does not exist. Are you sure Docker is running?"
    exit 1
fi

mkdir -p src/main/scala

rm -f ./src/main/scala/com/youtoo/Main.scala

mkdir -p ./src/main/scala/com/youtoo/

case "$1" in
  ingestion)
    echo "Building ingestion"
    cp ../ingestion/src/main/scala/com/youtoo/ingestion/IngestionBenchmarkServer.scala ./src/main/scala/com/youtoo/Main.scala
    ;;
  migration)
    echo "Building migration"
    cp ../data-migration/src/main/scala/com/youtoo/migration/MigratonBenchmarkServer.scala ./src/main/scala/com/youtoo/Main.scala
    ;;
  *)
    echo "Invalid option: $1"
    echo "Usage: $0 {ingestion|migration} {datadog|asprof|yourkit|jfr}"
    exit 1
    ;;
esac


cd "$(dirname "$0")/../"
BUILD_ARGS=$(grep -v '^#' .env | xargs -I {} echo --build-arg {} | xargs)

case "$2" in
  datadog)
    echo "Building image for Datadog"
    docker build $BUILD_ARGS --platform linux/arm64 -f ./profiling/Dockerfile.datadog --build-arg ARCH=$ARCH -t $TAG .
    ;;
  asprof)
    echo "Building image for Async-Profiler"
    docker build $BUILD_ARGS --platform linux/arm64 -f ./profiling/Dockerfile.asprof --build-arg ARCH=$ARCH -t $TAG .
    ;;
  yourkit)
    echo "Building image for YourKit"
    docker build $BUILD_ARGS --platform linux/arm64 -f ./profiling/Dockerfile.yourkit --build-arg ARCH=$ARCH -t $TAG .
    ;;
  jfr)
    echo "Building image for JFR"
    docker build $BUILD_ARGS --platform linux/arm64 -f ./profiling/Dockerfile.jfr --build-arg ARCH=$ARCH -t $TAG .
    ;;
  *)
    echo "Invalid option: $2"
    echo "Usage: $0 {ingestion|migration} {datadog|asprof|yourkit|jfr}"
    exit 1
    ;;
esac

docker tag $TAG youtoo-profiling:latest

