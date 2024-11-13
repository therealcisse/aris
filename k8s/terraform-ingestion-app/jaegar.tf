resource "kubernetes_namespace" "jaeger_namespace" {
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
  namespace         = kubernetes_namespace.jaeger_namespace.metadata[0].name
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
}

resource "helm_release" "jaeger_operator" {
  depends_on = [
    helm_release.cert_manager
  ]

  name       = "jaeger-operator"
  repository = local.helm_chart_repository
  chart      = "jaeger-operator"
  version    = var.jaeger_operator_chart_version
  namespace  = kubernetes_namespace.jaeger_namespace.metadata[0].name

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
}

resource "kubernetes_manifest" "jaeger" {
  depends_on = [
    helm_release.jaeger_operator
  ]

  computed_fields = ["spec.strategy"]

  manifest = {
    apiVersion = "jaegertracing.io/v1"
    kind       = "Jaeger"
    metadata = {
      namespace  = kubernetes_namespace.jaeger_namespace.metadata[0].name
      name = "simple-jaeger"
    }
    spec = {
      strategy = "allInOne"
      allInOne = {
        options = {
          query = {
            base-path = "/jaeger"
          }
        }
      }
      storage = {
        type = "memory"
        options = {
          memory = {
            "max-traces": "1000"
          }
        }
      }
      ingress = {
        enabled = true
      }
    }
  }

}

