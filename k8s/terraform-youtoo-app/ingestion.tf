resource "kubernetes_deployment" "youtoo_ingestion" {
  metadata {
    name      = "youtoo-ingestion"
    namespace = kubernetes_namespace.application_namespace.metadata[0].name
    labels = {
      app = "youtoo-ingestion"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "youtoo-ingestion"
      }
    }

    template {
      metadata {
        labels = {
          app = "youtoo-ingestion"
        }

        annotations = {
          "prometheus.io/scrape" = "true"
          "prometheus.io/path"   = "/metrics"
          "prometheus.io/port"   = "9464"
        }

      }

      spec {
        container {
          name              = "youtoo-ingestion"
          image             = "youtoo-ingestion:latest"
          image_pull_policy = "IfNotPresent"

          port {
            name           = "web"
            container_port = 8181
          }

          port {
            name           = "monitoring"
            container_port = 9464
          }

          # liveness_probe {
          #   http_get {
          #     path = "/health"
          #     port = 8181
          #   }
          #   initial_delay_seconds = 15
          #   period_seconds        = 15
          # }
          #
          # readiness_probe {
          #   http_get {
          #     path = "/health"
          #     port = 8181
          #   }
          #   initial_delay_seconds = 3
          #   period_seconds        = 3
          # }

          env {
            name  = "DATABASE_URL"
            value = "jdbc:postgresql://${var.postgres_host}:5432/${var.postgres_db_name}"
          }

          env {
            name  = "DATABASE_USERNAME"
            value = var.postgres_db_username
          }

          env {
            name  = "DATABASE_PASSWORD"
            value = var.postgres_db_password
          }

          env {
            name  = "INGESTION_SNAPSHOTS_THRESHOLD"
            value = "64"
          }

          env {
            name  = "OBSERVABILITY_LOGGING_ENDPOINT"
            value = "http://${helm_release.seq.name}.${kubernetes_namespace.logging.metadata[0].name}.svc.cluster.local:5341"
          }

          resources {
            limits = {
              cpu    = "2"
              memory = "2Gi"

            }

            requests = {
              cpu    = "1"
              memory = "1Gi"
            }

          }
        }

        restart_policy = "Always"
      }
    }
  }
}

resource "kubernetes_service" "youtoo_ingestion_service" {
  metadata {
    name      = "youtoo-ingestion"
    namespace = kubernetes_namespace.application_namespace.metadata[0].name
    labels = {
      app = "youtoo-ingestion"
    }
  }

  spec {
    selector = {
      app = "youtoo-ingestion"
    }

    port {
      name        = "web"
      port        = 8181
      target_port = 8181
      protocol    = "TCP"
    }

    port {
      name        = "monitoring"
      port        = 9464
      target_port = 9464
      protocol    = "TCP"
    }

    type = "ClusterIP"
  }
}

resource "kubectl_manifest" "youtoo_ingestion_service_monitor" {
  depends_on = [
    kubernetes_deployment.youtoo_ingestion
  ]

  yaml_body = <<YAML
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: youtoo-ingestion-service-monitor
  namespace: ${kubernetes_namespace.monitoring.metadata[0].name}
  labels:
    app.kubernetes.io/name: ${kubernetes_deployment.youtoo_ingestion.metadata[0].name}
    app.kubernetes.io/part-of: prometheus
    app: ${kubernetes_deployment.youtoo_ingestion.metadata[0].name}
    group: ${kubernetes_namespace.monitoring.metadata[0].name}
    release: prometheus-operator
spec:
  selector:
    matchLabels:
      app: ${kubernetes_service.youtoo_ingestion_service.metadata[0].name}
  endpoints:
    - port: "monitoring"
      path: "/metrics"
      interval: "30s"
  namespaceSelector:
    matchNames:
      - ${kubernetes_namespace.application_namespace.metadata[0].name}
      - ${kubernetes_namespace.monitoring.metadata[0].name}
YAML

}

