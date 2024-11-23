resource "time_sleep" "wait_for_infra" {

  depends_on = [
    kubectl_manifest.jaeger,
    kubectl_manifest.otel_collector,
    helm_release.prometheus_operator,
    helm_release.seq,
  ]

  create_duration = "90s"
}


resource "kubernetes_deployment" "youtoo_ingestion" {
  depends_on = [
    time_sleep.wait_for_infra
  ]

  metadata {
    name      = "youtoo-ingestion"
    namespace = kubernetes_namespace.application_namespace.metadata[0].name
    labels = {
      app = "youtoo-ingestion"
    }
    annotations = {
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

          "app.kubernetes.io/name"    = "youtoo-ingestion"
          "app.kubernetes.io/version" = "1.0.0"
          "app.kubernetes.io/part-of" = kubernetes_namespace.application_namespace.metadata[0].name
        }

        annotations = {
          "sidecar.opentelemetry.io/inject" = "${kubernetes_namespace.telemetry.metadata[0].name}/youtoo-otel"
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
            name  = "TELEMETRY_LOGGING_ENDPOINT"
            value = "http://${helm_release.seq.name}.${kubernetes_namespace.telemetry.metadata[0].name}.svc.cluster.local:5341/ingest/otlp/v1/logs"
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
      name        = "metrics"
      port        = 8889
      target_port = 8889
      protocol    = "TCP"
    }

    type = "ClusterIP"
  }
}

