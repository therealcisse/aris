resource "kubernetes_namespace" "logging" {
  lifecycle {
    ignore_changes = [
      metadata
    ]
  }

  metadata {
    name = "logging"
  }
}

resource "helm_release" "seq" {
  name       = "seq"
  repository = "https://helm.datalust.co"
  chart      = "seq"
  version    = "v2024.3.1"
  namespace  = kubernetes_namespace.logging.metadata[0].name

  create_namespace = false

  values = [
    file("${path.module}/seq_values.yaml")
  ]

}
