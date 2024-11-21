resource "helm_release" "prometheus_operator_crds" {
  name       = "prometheus-operator-crds"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "prometheus-operator-crds"
  version    = "16.0.0"

  namespace        = kubernetes_namespace.telemetry.metadata[0].name
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

  namespace        = kubernetes_namespace.telemetry.metadata[0].name
  create_namespace = false

  values = [
    file("${path.module}/prometheus-values.yaml")

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

