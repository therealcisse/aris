resource "kubernetes_config_map" "fluent_bit_ingestion_config" {
  metadata {
    name      = "fluent-bit-ingestion-config"
    namespace = kubernetes_namespace.application_namespace.metadata[0].name
  }

  data = {
    "fluent-bit.conf" = <<EOT

[SERVICE]
    Flush        1
    Daemon       Off
    Log_Level    info
    Parsers_File parsers.conf
    tls          On
    tls.verify   Off

[INPUT]
    Name              tail
    Path              /var/log/pods/${kubernetes_namespace.application_namespace.metadata[0].name}_$${POD_NAME}_$${POD_UID}/youtoo-ingestion/*.log
    PARSER            docker
    Refresh_Interval  5
    Tag               youtoo.ingestion.*
    Mem_Buf_Limit     50MB
    Skip_Long_Lines   On

[FILTER]
    Name                kubernetes
    Match               kube.*
    Merge_Log           On
    Keep_Log            Off
    K8S-Logging.Parser  On
    K8S-Logging.Exclude On
    Annotations         On
    Labels              On
    tls.verify          Off

[OUTPUT]
    Name                    gelf
    Match                   youtoo.ingestion.*
    Host                    ${helm_release.seq.name}.${kubernetes_namespace.telemetry.metadata[0].name}.svc.cluster.local
    Port                    12201
    Mode                    tcp
    Gelf_Short_Message_Key  data
    Compress                On
    Retry_Limit             False
    tls                     on

EOT
  }
}

