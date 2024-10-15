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

rm -f ./src/main/scala/com/youtoo/cqrs/example/Main.scala 2>/dev/null
rm -f ./src/main/resources/migrations/V1__cqrs.sql 2>/dev/null
rm -f ./src/main/resources/migrations/V2__ingestions.sql 2>/dev/null

cp ../cqrs-example-ingestion/src/main/scala/com/youtoo/cqrs/example/BenchmarkServer.scala ./src/main/scala/com/youtoo/cqrs/example/Main.scala
cp ../cqrs-persistence-postgres/src/main/resources/migrations/V1__cqrs.sql ./src/main/resources/migrations/V1__cqrs.sql
cp ../cqrs-example-ingestion/src/main/resources/migrations/V2__ingestions.sql ./src/main/resources/migrations/V2__ingestions.sql

cd "$(dirname "$0")/../"
docker build -f ./profiling/Dockerfile --build-arg ARCH=$ARCH -t $TAG .
docker tag $TAG cqrs-profiling:latest

