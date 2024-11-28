resource "helm_release" "jaeger_operator" {
  depends_on = [
    helm_release.cert_manager
  ]

  name       = "jaeger-operator"
  repository = "https://jaegertracing.github.io/helm-charts"
  chart      = "jaeger-operator"
  version    = var.jaeger_operator_chart_version
  namespace  = kubernetes_namespace.telemetry.metadata[0].name

  create_namespace = false

  timeout = 3600

  set {
    name  = "rbac.clusterRole"
    value = true
  }

  values = [
    yamlencode({
      extraEnv = [
      ]

    })
  ]

  wait = true
}

resource "time_sleep" "wait_for_jaeger_crd" {

  depends_on = [
    helm_release.jaeger_operator
  ]

  create_duration = "30s"
}

resource "kubectl_manifest" "jaeger" {
  depends_on = [
    time_sleep.wait_for_jaeger_crd
  ]

  server_side_apply = true

  yaml_body = yamlencode({
    apiVersion = "jaegertracing.io/v1"
    kind       = "Jaeger"
    metadata = {
      namespace = kubernetes_namespace.telemetry.metadata[0].name
      name      = "simple-jaeger"
    }
    spec = {
      strategy = "allInOne"
      allInOne = {
        image = "jaegertracing/all-in-one:latest"
        options = {
          log-level = "DEBUG"

          query = {
            base-path = "/jaeger"
          }

          prometheus = {
            server-url = "http://prometheus-operated.${kubernetes_namespace.telemetry.metadata[0].name}.svc.cluster.local:9090"

            query = {
              normalize-calls    = true
              normalize-duration = true
            }

          }

          "metrics-backend" = "prometheus"

        }
        metricsStorage = {
          type = "prometheus"
        }
      }
      storage = {
        type = "memory"
        options = {
          memory = {
            max-traces = "100000"
          }
        }
      }

    }
  })
}

resource "kubectl_manifest" "jaeger_pod_monitor" {
  depends_on = [
    kubectl_manifest.jaeger,
    kubectl_manifest.otel_collector,
  ]

  server_side_apply = true

  yaml_body = yamlencode({
    apiVersion = "monitoring.coreos.com/v1"
    kind       = "PodMonitor"
    metadata = {
      name      = "jaeger-components"
      namespace = kubernetes_namespace.telemetry.metadata[0].name
      labels = {
        release = "prometheus-operator"
      }
    }
    spec = {
      podMetricsEndpoints = [
        {
          port     = "admin-http"
          interval = "15s"
        }
      ]
      namespaceSelector = {
        matchNames = [
          kubernetes_namespace.telemetry.metadata[0].name,
        ]
      }
      selector = {
        matchLabels = {
          app = "jaeger"
        }
      }
    }

  })
}
