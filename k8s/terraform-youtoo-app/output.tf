output "dashboard_token" {
  value     = kubernetes_secret.dashboard_admin_token.data.token
  sensitive = true
}

output "kubernetes_dashboard_service_metadata" {
  value = helm_release.kubernetes_dashboard.metadata
}

output "metrics_server_service_metadata" {
  value = helm_release.metrics_server.metadata
}

