resource "kubernetes_ingress_v1" "youtoo_ingress" {
  depends_on = [
    kubernetes_deployment.youtoo_ingestion,
    kubernetes_service.youtoo_ingestion_service,
  ]

  metadata {
    name      = "youtoo-ingress"
    namespace = kubernetes_namespace.application_namespace.metadata[0].name
    annotations = {
      "nginx.ingress.kubernetes.io/rewrite-target" = "/"
    }
  }

  spec {

    rule {
      host = var.host

      http {

        path {
          path      = "/"
          path_type = "Prefix"
          backend {
            service {

              name = "youtoo-ingestion"
              port {
                number = 8181
              }
            }
          }
        }

        #   path {
        #     path = "/jaeger"
        #     backend {
        #       service {
        #
        #         name = "jaeger"
        #         port {
        #           number = 16686
        #         }
        #       }
        #     }
        #   }
        #
        #   path {
        #     path = "/prometheus"
        #     backend {
        #       service {
        #
        #         name = "prometheus-operated"
        #         port {
        #           number = 9090
        #         }
        #       }
        #     }
        #   }
        #
        #   path {
        #     path = "/grafana"
        #     backend {
        #       service {
        #
        #         name = "prometheus-operated-grafana"
        #         port {
        #           number = 80
        #         }
        #       }
        #     }
        #   }
        #
        #   path {
        #
        #     path = "/seq"
        #     backend {
        #       service {
        #
        #         name = "seq"
        #         port {
        #           number = 80
        #         }
        #       }
        #     }
        #   }

      }

    }

  }

}

