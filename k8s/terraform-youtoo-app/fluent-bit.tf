resource "kubernetes_config_map" "fluent_bit_ingestion_config" {
  metadata {
    name      = "fluent-bit-ingestion-config"
    namespace = kubernetes_namespace.application_namespace.metadata[0].name
  }

  data = {
    "parsers.conf" = <<EOT

[PARSER]
    Name        log_parser
    Format      regex
    Regex       ^(?<timestamp>[^\s]+)\s(?<stream>[^\s]+)\s(?<k8s_log_level>[^\s]+)\s(?<log_content>.*)$
    Time_Key    timestamp
    Time_Format %Y-%m-%dT%H:%M:%S.%L%z
    Decode_Field_As json log_content

EOT

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
    Path              /var/log/containers/$${POD_NAME}_${kubernetes_namespace.application_namespace.metadata[0].name}_youtoo-ingestion-*.log
    PARSER            log_parser
    Refresh_Interval  5
    Tag               kube.youtoo.ingestion.*
    Mem_Buf_Limit     64MB
    Skip_Long_Lines   On

[FILTER]
    Name                  record_modifier
    Match                 kube.youtoo.ingestion.*
    Remove_key            k8s_log_level
    Remove_key            stream
    Record  k8s.pod.name  $${POD_NAME}

[FILTER]
    Name              nest
    Match             kube.youtoo.ingestion.*
    Operation         lift
    Nested_under      log_content

[OUTPUT]
    Name                    gelf
    Match                   kube.youtoo.ingestion.*
    Host                    ${helm_release.seq.name}.${kubernetes_namespace.telemetry.metadata[0].name}.svc.cluster.local
    Port                    12201
    Mode                    tcp
    Gelf_Short_Message_Key  message
    Compress                On
    Retry_Limit             False
    tls                     Off
    tls.verify              Off

EOT
  }
}


