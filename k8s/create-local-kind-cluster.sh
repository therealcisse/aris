#!/bin/sh

set -e

kind create cluster -v -q --config ./kind-config.yaml --name youtoo

kubectl cluster-info --context kind-youtoo

kind load docker-image youtoo-ingestion:latest --name youtoo
# kind load docker-image youtoo-migration:latest --name youtoo

# kubectl taint nodes youtoo-control-plane node-role.kubernetes.io/control-plane=:NoSchedule
