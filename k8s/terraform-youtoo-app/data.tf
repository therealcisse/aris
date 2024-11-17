data "kubernetes_service" "jaeger_collector" {
  metadata {
    name      = "simple-jaeger-collector"
    namespace = kubernetes_namespace.observability.metadata[0].name
  }

}

