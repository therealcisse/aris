#!/bin/bash
COMMIT_SHA=$(git rev-parse --short HEAD)
TAG="cqrs-profiling:$COMMIT_SHA"

if [[ $(uname -m) == 'arm64' ]]; then
    export ARCH="linux-arm-64"
else
    export ARCH="linux-x86-64"
fi

if [ ! -e "/var/run/docker.sock" ]; then
    echo "'/var/run/docker.sock' does not exist.  Are you sure Docker is running?"
    exit 1
fi

mkdir -p src/main/scala

rm -f ./src/main/scala/com/youtoo/ingestion/Main.scala

mkdir -p ./src/main/scala/com/youtoo/ingestion/

cp ../ingestion-ingestion/src/main/scala/com/youtoo/ingestion/BenchmarkServer.scala ./src/main/scala/com/youtoo/ingestion/Main.scala

cd "$(dirname "$0")/../"
docker build -f ./profiling/Dockerfile --build-arg ARCH=$ARCH -t $TAG .
docker tag $TAG cqrs-profiling:latest

