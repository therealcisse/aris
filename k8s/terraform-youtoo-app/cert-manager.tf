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


