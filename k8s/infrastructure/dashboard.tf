# resource "kubernetes_namespace" "kubernetes_dashboard" {
#   metadata {
#     name = "kubernetes-dashboard"
#   }
# }
#
# resource "helm_release" "kubernetes_dashboard" {
#   depends_on = [
#     kubernetes_namespace.kubernetes_dashboard
#   ]
#
#   name       = "kubernetes-dashboard"
#   repository = "https://kubernetes.github.io/dashboard/"
#   chart      = "kubernetes-dashboard"
#   namespace  = kubernetes_namespace.kubernetes_dashboard.metadata[0].name
#
#   set {
#     name  = "service.type"
#     value = "LoadBalancer"
#   }
#
#   set {
#     name  = "service.externalPort"
#     value = "9080"
#   }
#
#   set {
#     name  = "protocolHttp"
#     value = "true"
#   }
#
#   set {
#     name  = "rbac.clusterReadOnlyRole"
#     value = "true"
#   }
#
#   set {
#     name  = "metricsScraper.enabled"
#     value = "true"
#   }
#
#   wait = true
# }
#
# resource "helm_release" "metrics_server" {
#   depends_on = [
#     helm_release.kubernetes_dashboard,
#     kubernetes_namespace.kubernetes_dashboard
#   ]
#
#   name       = "metrics-server"
#   repository = "https://kubernetes-sigs.github.io/metrics-server/"
#   chart      = "metrics-server"
#   namespace  = kubernetes_namespace.kubernetes_dashboard.metadata[0].name
#
#   set {
#     name  = "args"
#     value = "{--kubelet-insecure-tls=true}"
#   }
#
#   wait = true
# }
#
# resource "kubernetes_service_account" "dashboard_admin_sa" {
#   metadata {
#     name      = "admin-user"
#     namespace = kubernetes_namespace.kubernetes_dashboard.metadata[0].name
#   }
# }
#
# resource "kubernetes_secret" "dashboard_admin_token" {
#   metadata {
#     name      = "admin-user-token"
#     namespace = kubernetes_namespace.kubernetes_dashboard.metadata[0].name
#     annotations = {
#       "kubernetes.io/service-account.name" = kubernetes_service_account.dashboard_admin_sa.metadata[0].name
#     }
#   }
#
#   type = "kubernetes.io/service-account-token"
#
#   depends_on = [
#     helm_release.kubernetes_dashboard,
#     kubernetes_namespace.kubernetes_dashboard,
#   ]
# }
#
# resource "kubernetes_cluster_role_binding" "dashboard_admin_crb" {
#   metadata {
#     name = "admin-user-binding"
#   }
#
#   role_ref {
#     api_group = "rbac.authorization.k8s.io"
#     kind      = "ClusterRole"
#     name      = "cluster-admin"
#   }
#
#   subject {
#     kind      = "ServiceAccount"
#     name      = kubernetes_service_account.dashboard_admin_sa.metadata[0].name
#     namespace = kubernetes_namespace.kubernetes_dashboard.metadata[0].name
#   }
#
#   depends_on = [
#     kubernetes_namespace.kubernetes_dashboard,
#     kubernetes_service_account.dashboard_admin_sa
#   ]
# }
#
