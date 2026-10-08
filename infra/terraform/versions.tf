terraform {
  required_version = "~> 1.13.0"

  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 3.3.0"
    }
  }

  backend "kubernetes" {
    secret_suffix = "crm"
    namespace     = "student08"
  }
}

provider "kubernetes" {}
