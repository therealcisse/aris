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
            name  = "YOUTOO_LOG_LEVEL"
            value = var.log_level
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

        container {
          name  = "fluent-bit"
          image = "cr.fluentbit.io/fluent/fluent-bit:3.2.1"

          args = [
            "-c",
            "/fluent-bit/etc/fluent-bit.conf"
          ]

          volume_mount {
            name       = "fluent-bit-config"
            mount_path = "/fluent-bit/etc/fluent-bit.conf"
            sub_path   = "fluent-bit.conf"
            read_only  = true
          }


          volume_mount {
            name       = "varlog"
            mount_path = "/var/log"
            read_only  = true
          }

          volume_mount {
            name       = "varlibdockercontainers"
            mount_path = "/var/lib/docker/containers"
            read_only  = true
          }

          volume_mount {
            name       = "etcmachineid"
            mount_path = "/etc/machine-id"
            read_only  = true
          }

          env {
            name = "POD_NAME"
            value_from {
              field_ref {
                field_path = "metadata.name"
              }
            }
          }

          env {
            name = "POD_UID"
            value_from {
              field_ref {
                field_path = "metadata.uid"
              }
            }
          }

          # liveness_probe {
          #   http_get {
          #     path   = "/"
          #     port   = 8181
          #     scheme = "HTTP"
          #   }
          #   failure_threshold = 3
          #   period_seconds    = 10
          #   success_threshold = 1
          #   timeout_seconds   = 1
          # }
          #
          # readiness_probe {
          #   http_get {
          #     path   = "/api/v1/health"
          #     port   = 8181
          #     scheme = "HTTP"
          #   }
          #   failure_threshold = 3
          #   period_seconds    = 10
          #   success_threshold = 1
          #   timeout_seconds   = 1
          # }
        }

        volume {
          name = "fluent-bit-config"

          config_map {
            name = kubernetes_config_map.fluent_bit_ingestion_config.metadata[0].name
          }
        }

        volume {
          name = "varlog"

          host_path {
            path = "/var/log"
          }
        }

        volume {
          name = "varlibdockercontainers"

          host_path {
            path = "/var/lib/docker/containers"
          }
        }

        volume {
          name = "etcmachineid"

          host_path {
            path = "/etc/machine-id"
            type = "File"
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

