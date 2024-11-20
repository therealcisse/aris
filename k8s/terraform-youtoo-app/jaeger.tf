resource "kubernetes_namespace" "observability" {
  lifecycle {
    ignore_changes = [
      metadata
    ]
  }

  metadata {
    name = "observability"
  }
}

resource "helm_release" "cert_manager" {
  name              = "cert-manager"
  repository        = "https://charts.jetstack.io"
  chart             = "cert-manager"
  namespace         = kubernetes_namespace.observability.metadata[0].name
  create_namespace  = false
  version           = var.cert_manager_release
  dependency_update = true

  set {
    name  = "installCRDs"
    value = true
  }

  values = [
    yamlencode({
      replicaCount = 2
    })
  ]

  wait = true
}

resource "helm_release" "jaeger_operator" {
  depends_on = [
    helm_release.cert_manager
  ]

  name       = "jaeger-operator"
  repository = "https://jaegertracing.github.io/helm-charts"
  chart      = "jaeger-operator"
  version    = var.jaeger_operator_chart_version
  namespace  = kubernetes_namespace.observability.metadata[0].name

  create_namespace = false # Namespace is created earlier using kubernetes_namespace resource

  timeout = 3600

  set {
    name  = "rbac.clusterRole"
    value = true
  }

  values = [
    # You can add custom values if needed for the operator, for example:
    # "rbac.create=true"
    # "clusterRole.create=true"
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
      namespace = kubernetes_namespace.observability.metadata[0].name
      name      = "simple-jaeger"
    }
    spec = {
      strategy = "allInOne"
      allInOne = {
        options = {
          log-level = "debug"
          prometheus = {
            server-url = "http://prometheus-operated.${kubernetes_namespace.monitoring.metadata[0].name}.svc.cluster.local:9090"

            query = {
            }
          }
        }
        metricsStorage = {
          type = "prometheus"
        }
      }
      storage = {
        type = "memory"
        options = {
          memory = {
            max-traces = "10000"
          }
        }
      }
    }
  })
}
resource "kubectl_manifest" "jaeger_pod_monitor" {
  depends_on = [
    kubectl_manifest.jaeger
  ]

  server_side_apply = true

  yaml_body = yamlencode({
    apiVersion = "monitoring.coreos.com/v1"
    kind       = "PodMonitor"
    metadata = {
      name      = "jaeger-components"
      namespace = kubernetes_namespace.observability.metadata[0].name
      labels = {
        release = "prometheus-operator"
      }
    }
    spec = {
      podMetricsEndpoints = [
        {
          path     = "/metrics"
          port     = "admin-http"
          interval = "15s"
        }
      ]
      namespaceSelector = {
        matchNames = [
          kubernetes_namespace.observability.metadata[0].name,
          kubernetes_namespace.monitoring.metadata[0].name,
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
