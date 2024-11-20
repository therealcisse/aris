resource "kubernetes_namespace" "monitoring" {
  lifecycle {
    ignore_changes = [
      metadata
    ]
  }

  metadata {
    name = "monitoring"
  }
}

resource "helm_release" "prometheus_operator_crds" {
  name       = "prometheus-operator-crds"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "prometheus-operator-crds"
  version    = "16.0.0"

  namespace        = kubernetes_namespace.monitoring.metadata[0].name
  create_namespace = false

  values = [

  ]

  wait = true
}

resource "helm_release" "prometheus_operator" {
  depends_on = [
    helm_release.prometheus_operator_crds
  ]

  name       = "prometheus-operator"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  version    = "66.1.1"

  namespace        = kubernetes_namespace.monitoring.metadata[0].name
  create_namespace = false

  values = [
    file("${path.module}/monitoring-values.yaml")

  ]

  set {
    name = "server\\.resources"
    value = yamlencode({
      limits = {
        cpu    = "200m"
        memory = "50Mi"
      }
      requests = {
        cpu    = "100m"
        memory = "30Mi"
      }
    })
  }

  wait = true
}

# resource "kubernetes_service_account" "prometheus_operator_service_account" {
#   metadata {
#     name      = "prometheus-operator-service-account"
#     namespace = kubernetes_namespace.monitoring.metadata[0].name
#   }
# }

# resource "kubernetes_cluster_role" "prometheus_operator_cluster_role" {
#   metadata {
#     name = "prometheus-operator-cluster-role"
#   }
#   rule {
#     api_groups = [""]
#     resources  = ["pods", "nodes", "services", "endpoints", "persistentvolumeclaims", "events", "configmaps", "secrets"]
#     verbs      = ["get", "list", "watch"]
#   }
#   rule {
#     api_groups = ["apps"]
#     resources  = ["statefulsets"]
#     verbs      = ["get", "list", "watch"]
#   }
#   rule {
#     api_groups = ["monitoring.coreos.com"]
#     resources  = ["prometheuses", "alertmanagers", "servicemonitors", "podmonitors", "prometheusrules"]
#     verbs      = ["get", "list", "watch", "create", "update", "delete"]
#   }
# }

# resource "kubernetes_cluster_role_binding" "prometheus_operator_role_binding" {
#   metadata {
#     name = "prometheus-operator-role-binding"
#   }
#   role_ref {
#     api_group = "rbac.authorization.k8s.io"
#     kind      = "ClusterRole"
#     name      = kubernetes_cluster_role.prometheus_operator_cluster_role.metadata[0].name
#   }
#   subject {
#     kind      = "ServiceAccount"
#     name      = kubernetes_service_account.prometheus_operator_service_account.metadata[0].name
#     namespace = kubernetes_namespace.monitoring.metadata[0].name
#   }
# }

# resource "time_sleep" "wait_for_prometheus" {
#   depends_on = [
#     helm_release.prometheus_operator
#   ]
#
#   create_duration = "30s"
# }

# resource "kubectl_manifest" "prometheus_k8s" {
#   depends_on = [
#     time_sleep.wait_for_prometheus
#   ]
#
#   server_side_apply = true
#
#   yaml_body = yamlencode({
#     apiVersion = "monitoring.coreos.com/v1"
#     kind       = "Prometheus"
#     metadata = {
#       labels = {
#         "app.kubernetes.io/component" = "prometheus"
#         "app.kubernetes.io/instance"  = "k8s"
#         "app.kubernetes.io/name"      = "prometheus"
#         "app.kubernetes.io/part-of"   = "kube-prometheus"
#         "app.kubernetes.io/version"   = var.prometheus_version
#       }
#       name      = "k8s"
#       namespace = kubernetes_namespace.monitoring.metadata[0].name
#     }
#     spec = {
#       alerting = {
#         alertmanagers = [
#           {
#             apiVersion = "v2"
#             name       = "alertmanager-main"
#             namespace = kubernetes_namespace.monitoring.metadata[0].name
#             port       = "web"
#           }
#         ]
#       }
#       enableFeatures = []
#       externalLabels = {}
#       image          = "quay.io/prometheus/prometheus:v${var.prometheus_version}"
#       nodeSelector = {
#         "kubernetes.io/os" = "linux"
#       }
#       retention = "1d"
#       storage = {
#         volumeClaimTemplate = {
#           spec = {
#             accessModes = ["ReadWriteOnce"]
#             resources = {
#               requests = {
#                 storage = "1Gi"
#               }
#             }
#             storageClassName = "standard"
#           }
#         }
#       }
#
#       podMetadata = {
#         labels = {
#           "app.kubernetes.io/component" = "prometheus"
#           "app.kubernetes.io/instance"  = "k8s"
#           "app.kubernetes.io/name"      = "prometheus"
#           "app.kubernetes.io/part-of"   = "kube-prometheus"
#           "app.kubernetes.io/version"   = var.prometheus_version
#         }
#       }
#       enableRemoteWriteReceiver = true
#       podMonitorNamespaceSelector   = {}
#       podMonitorSelector            = {
#         matchLabels = {
#           group = kubernetes_namespace.monitoring.metadata[0].name
#         }
#
#       }
#       probeNamespaceSelector        = {}
#       probeSelector                 = {}
#       replicas                      = 1
#       resources = {
#         requests = {
#           memory = "400Mi"
#         }
#       }
#       ruleNamespaceSelector         = {}
#       ruleSelector                  = {}
#       scrapeConfigNamespaceSelector = {}
#       scrapeConfigSelector          = {}
#       securityContext = {
#         fsGroup      = 2000
#         runAsNonRoot = true
#         runAsUser    = 1000
#       }
#       serviceAccountName        = kubernetes_service_account.prometheus_operator_service_account.metadata[0].name
#       serviceMonitorNamespaceSelector = {}
#       serviceMonitorSelector        = {
#         matchLabels = {
#           group = kubernetes_namespace.monitoring.metadata[0].name
#         }
#
#       }
#       version                       = var.prometheus_version
#     }
#   })
# }
