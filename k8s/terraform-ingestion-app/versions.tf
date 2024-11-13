terraform {
  required_version = ">= 1.9.5"
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }

    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.16.1"

    }
  }
}

provider "helm" {
  kubernetes {
    config_path = "~/.kube/config"
  }
}
