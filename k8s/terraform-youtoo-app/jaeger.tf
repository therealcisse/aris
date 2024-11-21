resource "helm_release" "cert_manager" {
  name              = "cert-manager"
  repository        = "https://charts.jetstack.io"
  chart             = "cert-manager"
  namespace         = kubernetes_namespace.telemetry.metadata[0].name
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
  namespace  = kubernetes_namespace.telemetry.metadata[0].name

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
    helm_release.jaeger_operator,
    helm_release.eck_operator,
  ]

  create_duration = "30s"
}

resource "kubectl_manifest" "jaeger" {
  depends_on = [
    time_sleep.wait_for_jaeger_crd,

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
      strategy = "production"

      collector = {
        maxReplicas = 1

        resources = {
          limits = {
            cpu    = "100m"
            memory = "256Mi"
          }

          requests = {
            cpu    = "100m"
            memory = "128Mi"
          }
        }

      }

      storage = {
        type = "elasticsearch"
        options = {
          es = {
            server-urls = "https://elasticsearch-es-http.${kubernetes_namespace.elastic_system.metadata[0].name}:9200"
          }
        }
        # secretName = kubernetes_secret.jaeger_es_credentials.metadata[0].name
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
        release    = "prometheus-operator"
        monitoring = "enabled"
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
