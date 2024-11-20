resource "kubernetes_service_account" "otel_operator_service_account" {
  metadata {
    name      = "opentelemetry-operator-service-account"
    namespace = kubernetes_namespace.telemetry.metadata[0].name
  }
}

resource "helm_release" "opentelemetry_operator" {

  depends_on = [
    helm_release.cert_manager,
  ]


  name       = "opentelemetry-operator"
  namespace  = kubernetes_namespace.telemetry.metadata[0].name
  repository = "https://open-telemetry.github.io/opentelemetry-helm-charts"
  chart      = "opentelemetry-operator"
  version    = "0.74.2"

  values = [
    yamlencode({
      manager = {

        serviceAccount = {
          create = false
          name   = kubernetes_service_account.otel_operator_service_account.metadata[0].name
        }

        collectorImage = {
          repository = "otel/opentelemetry-collector-contrib"
        }

      }
    })
  ]
}

resource "time_sleep" "wait_for_otel_operator" {

  depends_on = [
    helm_release.opentelemetry_operator,
  ]

  create_duration = "120s"
}


resource "time_sleep" "wait_for_jaeger" {

  depends_on = [
    time_sleep.wait_for_otel_operator,
  ]

  create_duration = "30s"
}

resource "kubectl_manifest" "otel_collector" {
  depends_on = [
    time_sleep.wait_for_jaeger
  ]

  server_side_apply = true

  yaml_body = yamlencode({
    apiVersion = "opentelemetry.io/v1beta1"
    kind       = "OpenTelemetryCollector"
    metadata = {
      name      = "youtoo-otel"
      namespace = kubernetes_namespace.telemetry.metadata[0].name
    }
    spec = {
      config = {
        receivers = {
          hostmetrics = {
            scrapers = {

              cpu = {}
              memory = {}
              disk = {}
            }
          }
          otlp = {
            protocols = {
              grpc = {
                endpoint = "localhost:4317"
              }
              http = {
                endpoint = "localhost:4318"
                cors = {
                  allowed_origins = ["http://*", "https://*"]
                }
              }
            }
          }

          prometheus = {
            config = {
              scrape_configs = [
                {
                  job_name = "otelcol"
                  scrape_interval = "10s"
                  static_configs = [
                    {
                      targets = ["0.0.0.0:8888"]
                    }
                  ]
                }
              ]
            }
          }
        }
        processors = {
          memory_limiter = {
            check_interval         = "1s"
            limit_percentage       = 75
            spike_limit_percentage = 15
          }
          batch = {
            send_batch_size = 10000
            timeout         = "10s"
          }

        }
        connectors = {
          spanmetrics = {
            namespace = "span.metrics"
          }

        }
        exporters = {
          debug = {}
          prometheusremotewrite = {
            endpoint = "http://prometheus-operated.${kubernetes_namespace.telemetry.metadata[0].name}.svc.cluster.local:9090/api/v1/write"
            target_info = {
              enabled = true
            }
            tls = {
              insecure = true
            }
          }
          "otlp/jaeger" = {
            endpoint = "simple-jaeger-collector.${kubernetes_namespace.telemetry.metadata[0].name}.svc.cluster.local:4317"
            tls = {
              insecure = true
            }
          }
        }
        service = {
          pipelines = {
            traces = {
              receivers  = ["otlp"]
              processors = ["batch"]
              exporters  = ["otlp/jaeger"]
            }

            metrics = {
              receivers  = ["hostmetrics", "otlp", "prometheus"]
              processors = ["batch", "memory_limiter"]
              exporters  = ["prometheusremotewrite"]
            }

          }

          telemetry = {
            logs = {
              level = "debug"
            }

          }

        }
      }
      mode = "sidecar"
      ports = [
        {
          name       = "metrics"
          port       = 8888
          protocol   = "TCP"
          targetPort = 8888
        }
      ]
    }
  })
}

