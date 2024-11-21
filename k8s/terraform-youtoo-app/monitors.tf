resource "kubectl_manifest" "otel_collector_servicemonitor" {
  depends_on = [
    kubernetes_service.youtoo_ingestion_service,
    helm_release.prometheus_operator,
    kubectl_manifest.otel_collector,
  ]

  server_side_apply = true

  yaml_body = yamlencode({
    apiVersion = "monitoring.coreos.com/v1"
    kind       = "ServiceMonitor"
    metadata = {
      name      = "youtoo-ingestion-metrics"
      namespace = kubernetes_namespace.telemetry.metadata[0].name
      labels = {
        release    = "prometheus-operator"
        monitoring = "enabled"
      }
    }
    spec = {
      selector = {
        matchLabels = {
          app = "youtoo-ingestion"
        }
      }
      endpoints = [
        {
          port     = "metrics"
          interval = "15s"
        }
      ]
      namespaceSelector = {
        matchNames = [
          kubernetes_namespace.telemetry.metadata[0].name,
          kubernetes_namespace.application_namespace.metadata[0].name,
        ]
      }
    }
  })
}

