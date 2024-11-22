resource "kubernetes_service_account" "jaeger" {
  metadata {
    name = "jaeger-service-account"
    namespace  = kubernetes_namespace.telemetry.metadata[0].name
  }
}

resource "kubernetes_role" "jaeger_role" {
  metadata {
    name = "jaeger-role"
    namespace = kubernetes_namespace.telemetry.metadata[0].name
  }

  rule {
    # Allow all operations on Jaeger CRDs in the "telemetry" namespace
    api_groups = ["jaegertracing.io"]
    resources  = ["*"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }

}

resource "kubernetes_role_binding" "jaeger_role_binding" {
  metadata {
    name = "jaeger-role-binding"
    namespace = kubernetes_namespace.telemetry.metadata[0].name
  }

  subject {
    kind = "ServiceAccount"
    name = kubernetes_service_account.jaeger.metadata[0].name
    namespace = kubernetes_namespace.telemetry.metadata[0].name
  }

  role_ref {
    kind = "Role"
    name = kubernetes_role.jaeger.metadata[0].name
    api_group = "rbac.authorization.k8s.io"
  }
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

  create_namespace = false

  timeout = 3600

  set {
    name  = "rbac.clusterRole"
    value = true
  }

  set {
    name = "serviceAccount.name"
    value = kubernetes_service_account.jaeger.metadata[0].name
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
    time_sleep.wait_for_elasticsearch,
  ]

  create_duration = "120s"
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
        type    = "elasticsearch"

        options = {
          es = {
            server-urls = "https://${var.es_host}:9200"
            "index-prefix" = "youtoo"
            username = "elastic"
            password = var.es_password
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
