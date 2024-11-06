#!/bin/sh

$ASYNC_PROFILER_HOME/bin/asprof collect -g --fdtransfer -e cpu -t -d 30 -f /output/profile_cpu.html $1
