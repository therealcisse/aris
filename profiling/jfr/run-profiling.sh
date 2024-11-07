#!/bin/sh

jcmd $1 JFR.start duration=60s settings=profile filename=/output/recording.jfr

sleep 60

jcmd $1 JFR.stop name=1

/jfr-flame-graph/build/install/jfr-flame-graph/bin/jfr-flame-graph -f /output/recording.jfr | \
    $FLAMEGRAPH_DIR/flamegraph.pl --title "YouToo profiling flamegraph" > /output/flamegraph.svg

