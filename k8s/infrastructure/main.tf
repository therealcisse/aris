provider "kubernetes" {
  config_path = "~/.kube/config" # Points to your local kubeconfig file
  # config_path = kind_cluster.kind.kubeconfig_path
}

