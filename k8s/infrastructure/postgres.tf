resource "kubernetes_namespace" "database" {
  metadata {
    name = "database"
  }
}

resource "kubernetes_service" "postgres_host" {
  metadata {
    name      = "postgres-external"
    namespace = kubernetes_namespace.database.metadata[0].name
  }
  spec {
    type          = "ExternalName"
    external_name = var.postgres_host
  }
}
