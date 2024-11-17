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
    name  = "podSecurityPolicy.enabled"
    value = true
  }

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

resource "kubernetes_service_account" "prometheus_operator_service_account" {
  metadata {
    name      = "prometheus-operator-service-account"
    namespace = kubernetes_namespace.monitoring.metadata[0].name
  }
}

resource "kubernetes_cluster_role" "prometheus_operator_cluster_role" {
  metadata {
    name = "prometheus-operator-cluster-role"
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

resource "kubernetes_cluster_role_binding" "prometheus_operator_role_binding" {
  metadata {
    name = "prometheus-operator-role-binding"
  }
  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "ClusterRole"
    name      = kubernetes_cluster_role.prometheus_operator_cluster_role.metadata[0].name
  }
  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account.prometheus_operator_service_account.metadata[0].name
    namespace = kubernetes_namespace.monitoring.metadata[0].name
  }
}

resource "time_sleep" "wait_for_prometheus" {
  depends_on = [
    helm_release.prometheus_operator
  ]

  create_duration = "30s"
}

resource "kubectl_manifest" "prometheus" {
  depends_on = [
    time_sleep.wait_for_prometheus
  ]

  yaml_body = <<YAML
apiVersion: monitoring.coreos.com/v1
kind: Prometheus
metadata:
  name: prometheus
  namespace: ${kubernetes_namespace.monitoring.metadata[0].name}
  labels:
    app: prometheus
spec:
  replicas: 1
  serviceAccountName: ${kubernetes_service_account.prometheus_operator_service_account.metadata[0].name}
  serviceMonitorSelector:
    matchLabels:
      group: ${kubernetes_namespace.monitoring.metadata[0].name}
  podMonitorSelector:
    matchLabels:
      group: ${kubernetes_namespace.monitoring.metadata[0].name}
  retention: 15d
  storage:
    volumeClaimTemplate:
      spec:
        accessModes:
          - ReadWriteOnce
        resources:
          requests:
            storage: 1Gi
        storageClassName: standard
  alerting:
    alertmanagers:
      - name: alertmanager-main
        namespace: ${kubernetes_namespace.monitoring.metadata[0].name}
        port: web

YAML

}
