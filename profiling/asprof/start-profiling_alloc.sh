#!/bin/sh

$ASYNC_PROFILER_HOME/bin/asprof collect -g --fdtransfer -e alloc -t -d 30 -f /output/profile_alloc.html $1

