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
    <<EOF
prometheusOperator:
  enabled: true
EOF
  ]

  set {
    name  = "podSecurityPolicy.enabled"
    value = true
  }

  set {
    name  = "server.persistentVolume.enabled"
    value = false
  }

  # You can provide a map of value using yamlencode. Don't forget to escape the last element after point in the name
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

}

resource "kubernetes_service_account" "prometheus_operator" {
  metadata {
    name      = "prometheus-operator"
    namespace = kubernetes_namespace.monitoring.metadata[0].name
  }
}

resource "kubernetes_cluster_role" "prometheus_operator" {
  metadata {
    name = "prometheus-operator"
  }
  rule {
    api_groups = [""]
    resources  = ["pods", "nodes", "services", "endpoints", "persistentvolumeclaims", "events", "configmaps", "secrets"]
    verbs      = ["get", "list", "watch"]
  }
  rule {
    api_groups = ["apps"]
    resources  = ["statefulsets"]
    verbs      = ["get", "list", "watch"]
  }
  rule {
    api_groups = ["monitoring.coreos.com"]
    resources  = ["prometheuses", "alertmanagers", "servicemonitors", "podmonitors", "prometheusrules"]
    verbs      = ["get", "list", "watch", "create", "update", "delete"]
  }
}

resource "kubernetes_cluster_role_binding" "prometheus_operator" {
  metadata {
    name = "prometheus-operator"
  }
  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "ClusterRole"
    name      = kubernetes_cluster_role.prometheus_operator.metadata[0].name
  }
  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account.prometheus_operator.metadata[0].name
    namespace = kubernetes_namespace.monitoring.metadata[0].name
  }
}

resource "kubernetes_manifest" "prometheus" {
  manifest = {
    "apiVersion" = "monitoring.coreos.com/v1"
    "kind"       = "Prometheus"
    "metadata" = {
      "name"      = "prometheus"
      "namespace" = kubernetes_namespace.monitoring.metadata[0].name
      "labels" = {
        "app" = "prometheus"
      }
    }
    "spec" = {
      "replicas"          = 2
      "serviceAccountName" = "prometheus-operator-prometheus"
      "retention"         = "15d"
      # "resources" = {
      #   "requests" = {
      #     "memory" = "2Gi"
      #     "cpu"    = "500m"
      #   }
      #   "limits" = {
      #     "memory" = "4Gi"
      #     "cpu"    = "1000m"
      #   }
      # }
      # "storage" = {
      #   "volumeClaimTemplate" = {
      #     "spec" = {
      #       "accessModes" = ["ReadWriteOnce"]
      #       "resources" = {
      #         "requests" = {
      #           "storage" = "50Gi"
      #         }
      #       }
      #       "storageClassName" = "standard" # Adjust this value to match your cluster's storage class
      #     }
      #   }
      # }
      "alerting" = {
        "alertmanagers" = [
          {
            "name"      = "alertmanager-main"
            "namespace" = kubernetes_namespace.monitoring.metadata[0].name
            "port"      = "web"
          }
        ]
      }
    }
  }
}

