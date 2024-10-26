#!/bin/bash
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

# Check if exactly one argument is provided
if [ $# -ne 1 ]; then
  echo "Usage: $0 {ingestion|migration}"
  exit 1
fi

# Use a case statement to match the argument
case "$1" in
  ingestion)
    echo "Building ingestion"
    cp ../ingestion/src/main/scala/com/youtoo/ingestion/BenchmarkServer.scala ./src/main/scala/com/youtoo/Main.scala
    ;;
  migration)
    echo "Building migration"
    cp ../data-migration/src/main/scala/com/youtoo/migration/BenchmarkServer.scala ./src/main/scala/com/youtoo/Main.scala
    ;;
  *)
    echo "Invalid option: $1"
    echo "Usage: $0 {ingestion|migration}"
    exit 1
    ;;
esac


cd "$(dirname "$0")/../"
BUILD_ARGS=$(grep -v '^#' .env | xargs -I {} echo --build-arg {} | xargs)
docker build $BUILD_ARGS -f ./profiling/Dockerfile --build-arg ARCH=$ARCH -t $TAG .
docker tag $TAG youtoo-profiling:latest

