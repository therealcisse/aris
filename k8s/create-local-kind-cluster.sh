#!/bin/sh

set -e

kind create cluster --config ./kind-config.yaml --name youtoo

kind load docker-image youtoo-ingestion:latest --name youtoo
# kind load docker-image youtoo-migration:latest --name youtoo

# kubectl taint nodes youtoo-control-plane node-role.kubernetes.io/control-plane=:NoSchedule
