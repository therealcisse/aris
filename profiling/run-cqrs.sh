#!/bin/bash

COMMIT_SHA=$(git rev-parse --short HEAD)
docker run -i --network cqrs_cqrs-net -p 8181:8181 -e DATABASE_URL=$DATABASE_URL -e DATABASE_USERNAME=$DATABASE_USERNAME -e DATABASE_PASSWORD=$DATABASE_PASSWORD -p 10001:10001 --rm cqrs-profiling:$COMMIT_SHA

