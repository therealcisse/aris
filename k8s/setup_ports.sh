#!/bin/sh

set -e

kubectl -n youtoo port-forward svc/youtoo-ingestion 8181:8181 &
kubectl -n monitoring port-forward svc/prometheus-operator-grafana  3000:80 &
kubectl -n logging port-forward svc/seq  4000:80 &
kubectl -n observability port-forward svc/simple-jaeger-query 16686 &
kubectl -n monitoring port-forward svc/prometheus-operated 9090 &

