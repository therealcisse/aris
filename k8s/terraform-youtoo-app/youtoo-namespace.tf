resource "kubernetes_namespace" "application_namespace" {
  metadata {
    name = "youtoo"
  }
}

