resource "kubernetes_service_account" "otel_operator_service_account" {
  metadata {
    name      = "opentelemetry-operator-service-account"
    namespace = kubernetes_namespace.observability.metadata[0].name
  }
}

resource "helm_release" "opentelemetry_operator" {

  depends_on = [
    helm_release.cert_manager,
  ]


  name       = "opentelemetry-operator"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  repository = "https://open-telemetry.github.io/opentelemetry-helm-charts"
  chart      = "opentelemetry-operator"
  version    = "0.74.2"

  values = [
    yamlencode({
      manager = {

        serviceAccount = {
          create = false
          name   = kubernetes_service_account.otel_operator_service_account.metadata[0].name
        }

        collectorImage = {
          repository = "otel/opentelemetry-collector-contrib"
        }

      }
    })
  ]
}

resource "time_sleep" "wait_for_otel_operator" {

  depends_on = [
    helm_release.opentelemetry_operator,
  ]

  create_duration = "120s"
}


resource "time_sleep" "wait_for_jaeger" {

  depends_on = [
    time_sleep.wait_for_otel_operator,
  ]

  create_duration = "30s"
}

resource "kubectl_manifest" "otel_collector" {
  depends_on = [
    time_sleep.wait_for_jaeger
  ]

  yaml_body = yamlencode({
    apiVersion = "opentelemetry.io/v1alpha1"
    kind       = "OpenTelemetryCollector"
    metadata = {
      name      = "youtoo-otelcol"
      namespace = kubernetes_namespace.monitoring.metadata[0].name
    }
    spec = {
      config = file("${path.module}/otel-collector-config.yml")
      mode   = "sidecar"
      ports = [
        {
          name       = "metrics"
          port       = 8889
          protocol   = "TCP"
          targetPort = 8889
        }
      ]
    }
  })
}

