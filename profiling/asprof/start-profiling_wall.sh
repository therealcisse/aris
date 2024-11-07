#!/bin/sh

$ASYNC_PROFILER_HOME/bin/asprof collect -g --fdtransfer -e wall -t -d 30 -f /output/profile_wall.html $1


