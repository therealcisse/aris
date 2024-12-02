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

    time = {
      source  = "hashicorp/time"
      version = "0.12.1"
    }

    kubectl = {
      source  = "alekc/kubectl"
      version = "2.1.3"
    }

  }

}

provider "helm" {
  kubernetes {
    config_path = "~/.kube/config" # Points to your local kubeconfig file
  }
}

provider "kubectl" {
}

