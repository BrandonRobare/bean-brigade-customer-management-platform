resource "kubernetes_network_policy_v1" "default_deny" {
  metadata {
    name      = "crm-default-deny"
    namespace = var.namespace
    labels    = local.part_of
  }
  spec {
    pod_selector {
      match_labels = local.part_of
    }
    policy_types = ["Ingress", "Egress"]
  }
}

resource "kubernetes_network_policy_v1" "dns" {
  metadata {
    name      = "crm-dns"
    namespace = var.namespace
    labels    = local.part_of
  }
  spec {
    pod_selector {
      match_labels = local.part_of
    }
    policy_types = ["Egress"]
    egress {
      to {
        namespace_selector {
          match_labels = { "kubernetes.io/metadata.name" = "kube-system" }
        }
      }
      ports {
        protocol = "UDP"
        port     = "53"
      }
      ports {
        protocol = "TCP"
        port     = "53"
      }
    }
  }
}

resource "kubernetes_network_policy_v1" "ingress" {
  metadata {
    name      = "crm-ingress"
    namespace = var.namespace
    labels    = local.part_of
  }
  spec {
    pod_selector {
      match_expressions {
        key      = "app.kubernetes.io/name"
        operator = "In"
        values   = ["crm-api", "crm-ui"]
      }
    }
    policy_types = ["Ingress"]
    ingress {
      from {
        namespace_selector {
          match_labels = { "kubernetes.io/metadata.name" = var.ingress_namespace }
        }
      }
      ports {
        protocol = "TCP"
        port     = "8080"
      }
    }
  }
}

resource "kubernetes_network_policy_v1" "api_egress" {
  metadata {
    name      = "crm-api-egress"
    namespace = var.namespace
    labels    = local.part_of
  }
  spec {
    pod_selector {
      match_labels = local.app["crm-api"]
    }
    policy_types = ["Egress"]
    egress {
      to {
        pod_selector {
          match_labels = local.app["crm-postgres"]
        }
      }
      ports {
        protocol = "TCP"
        port     = "5432"
      }
    }
    egress {
      to {
        pod_selector {
          match_labels = local.app["crm-kafka"]
        }
      }
      ports {
        protocol = "TCP"
        port     = "9092"
      }
    }
  }
}

resource "kubernetes_network_policy_v1" "postgres_ingress" {
  metadata {
    name      = "crm-postgres-ingress"
    namespace = var.namespace
    labels    = local.part_of
  }
  spec {
    pod_selector {
      match_labels = local.app["crm-postgres"]
    }
    policy_types = ["Ingress"]
    ingress {
      from {
        pod_selector {
          match_labels = local.app["crm-api"]
        }
      }
      ports {
        protocol = "TCP"
        port     = "5432"
      }
    }
  }
}

resource "kubernetes_network_policy_v1" "kafka" {
  metadata {
    name      = "crm-kafka"
    namespace = var.namespace
    labels    = local.part_of
  }
  spec {
    pod_selector {
      match_labels = local.app["crm-kafka"]
    }
    policy_types = ["Ingress", "Egress"]
    ingress {
      from {
        pod_selector {
          match_expressions {
            key      = "app.kubernetes.io/name"
            operator = "In"
            values   = ["crm-api", "crm-kafka-topics"]
          }
        }
      }
      ports {
        protocol = "TCP"
        port     = "9092"
      }
    }
    ingress {
      from {
        pod_selector {
          match_labels = local.app["crm-kafka"]
        }
      }
      ports {
        protocol = "TCP"
        port     = "9092"
      }
      ports {
        protocol = "TCP"
        port     = "9093"
      }
    }
    egress {
      to {
        pod_selector {
          match_labels = local.app["crm-kafka"]
        }
      }
      ports {
        protocol = "TCP"
        port     = "9092"
      }
      ports {
        protocol = "TCP"
        port     = "9093"
      }
    }
  }
}

resource "kubernetes_network_policy_v1" "topics_egress" {
  metadata {
    name      = "crm-topics-egress"
    namespace = var.namespace
    labels    = local.part_of
  }
  spec {
    pod_selector {
      match_labels = local.app["crm-kafka-topics"]
    }
    policy_types = ["Egress"]
    egress {
      to {
        pod_selector {
          match_labels = local.app["crm-kafka"]
        }
      }
      ports {
        protocol = "TCP"
        port     = "9092"
      }
    }
  }
}
