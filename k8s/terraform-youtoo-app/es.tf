# resource "kubernetes_namespace" "elastic_system" {
#   metadata {
#     name = "elastic-system"
#   }
# }
#
# # resource "helm_release" "eck_operator_crds" {
# #   name              = "eck-operator-crds"
# #   repository        = "https://helm.elastic.co"
# #   chart             = "eck-operator-crds"
# #   namespace         = kubernetes_namespace.elastic_system.metadata[0].name
# #   create_namespace  = false
# #   version           = "2.15.0"
# #   dependency_update = true
# #
# #   values = [
# #   ]
# #
# #   wait = true
# # }
#
# resource "helm_release" "eck_operator" {
#   depends_on = [
#     # helm_release.eck_operator_crds,
#   ]
#
#   name              = "eck-operator"
#   repository        = "https://helm.elastic.co"
#   chart             = "eck-operator"
#   namespace         = kubernetes_namespace.elastic_system.metadata[0].name
#   create_namespace  = false
#   version           = "2.15.0"
#   dependency_update = true
#
#   set {
#     name  = "installCRDs"
#     value = true
#   }
#
#   values = [
#     yamlencode({
#       resources = {
#         requests = {
#           cpu    = "100m"
#           memory = "128Mi"
#         }
#         limits = {
#           cpu    = "500m"
#           memory = "1Gi"
#         }
#       }
#
#       volumeClaimTemplate = {
#
#         accessModes =  ["ReadWriteOnce"]
#         resources = {
#           requests = {
#
#             storage = "2Gi"
#           }
#
#         }
#       }
#
#     })
#   ]
#
#   wait = true
# }
#
# // https://www.elastic.co/guide/en/cloud-on-k8s/master/k8s-deploy-elasticsearch.html
# resource "kubectl_manifest" "elasticsearch" {
#   depends_on = [
#     helm_release.eck_operator,
#   ]
#
#   server_side_apply = true
#
#   yaml_body = yamlencode({
#     apiVersion = "elasticsearch.k8s.elastic.co/v1"
#     kind       = "Elasticsearch"
#     metadata = {
#       name      = "elasticsearch"
#       namespace = kubernetes_namespace.elastic_system.metadata[0].name
#     }
#     spec = {
#       version = "8.16.1"
#       nodeSets = [
#         {
#           name  = "default"
#           count = 1
#           config = {
#             node = {
#               store = {
#                 allow_mmap = false
#               }
#             }
#             xpack = {
#               security = {
#                 enabled = true
#               }
#             }
#           }
#         }
#       ]
#     }
#   })
# }
#
# resource "time_sleep" "wait_for_elasticsearch" {
#
#   depends_on = [
#     helm_release.eck_operator,
#     kubectl_manifest.elasticsearch,
#   ]
#
#   create_duration = "120s"
# }
#
