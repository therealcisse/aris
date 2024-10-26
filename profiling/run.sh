#!/bin/bash

COMMIT_SHA=$(git rev-parse --short HEAD)
docker run -i --network youtoo_app-network -p 8181:8181 --env-file ../.env -p 10001:10001 --rm youtoo-profiling:$COMMIT_SHA

