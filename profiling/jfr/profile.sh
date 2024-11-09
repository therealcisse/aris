#!/bin/sh

set -e

if [ $# -ne 1 ]; then
  echo "Usage: $0 {run|status|sh}"
  exit 1
fi

tag=youtoo-profiling:latest

CONTAINER_ID=$(docker ps -q --filter "ancestor=$tag")
echo "Container ID: $CONTAINER_ID"

PID=$(docker exec $CONTAINER_ID jps | grep App | awk '{print $1}')
echo "Found PID: $PID"

# Use a case statement to match the argument
case "$1" in
  run)
    echo "Start profiling"
    docker exec -it $CONTAINER_ID /run-profiling.sh $PID
    docker cp $CONTAINER_ID:/output/flamegraph.svg $(pwd)/.cache/flamegraph.svg
    docker cp $CONTAINER_ID:/output/recording.jfr $(pwd)/.cache/recording.jfr
    ;;
  status)
    echo "Check profiling"
    docker exec -it $CONTAINER_ID /status-profiling.sh $PID
    ;;
  sh)
    echo "SH profiling"
    docker exec -it $CONTAINER_ID /bin/bash
    ;;
  *)
    echo "Invalid option: $1"
    echo "Usage: $0 {run|status|stop}"
    exit 1
    ;;
esac


