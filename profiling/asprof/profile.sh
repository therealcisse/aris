#!/bin/sh

set -e

if [ $# -ne 1 ]; then
  echo "Usage: $0 {cpu|alloc|wall|status|stop}"
  exit 1
fi

tag=youtoo-profiling:latest

CONTAINER_ID=$(docker ps -q --filter "ancestor=$tag")
echo "Container ID: $CONTAINER_ID"

PID=$(docker exec $CONTAINER_ID jps | grep BenchmarkServer | awk '{print $1}')
echo "Found PID: $PID"

# Use a case statement to match the argument
case "$1" in
  cpu)
    echo "Profiling CPU"
    docker exec -it $CONTAINER_ID /start-profiling_cpu.sh $PID
    docker cp $CONTAINER_ID:/output/profile_cpu.html $(pwd)/.cache/profile_cpu.html
    ;;
  alloc)
    echo "Profiling allocations"
    docker exec -it $CONTAINER_ID /start-profiling_alloc.sh $PID
    docker cp $CONTAINER_ID:/output/profile_alloc.html $(pwd)/.cache/profile_alloc.html
    ;;
  wall)
    echo "Profiling wall clock"
    docker exec -it $CONTAINER_ID /start-profiling_wall.sh $PID
    docker cp $CONTAINER_ID:/output/profile_wall.html $(pwd)/.cache/profile_wall.html
    ;;
  status)
    echo "Profiling status"
    docker exec -it $CONTAINER_ID /status-profiling.sh $PID
    ;;
  stop)
    echo "Stop profiling"
    docker exec -it $CONTAINER_ID /stop-profiling.sh $PID
    ;;
  *)
    echo "Invalid option: $1"
    echo "Usage: $0 {cpu|alloc|wall|status|stop}"
    exit 1
    ;;
esac


