resource "helm_release" "seq" {
  name       = "seq"
  repository = "https://helm.datalust.co"
  chart      = "seq"
  version    = "v2024.3.1"
  namespace  = kubernetes_namespace.telemetry.metadata[0].name

  create_namespace = false

  values = [
    file("${path.module}/seq-values.yaml")
  ]

}
