import {
  to = kubernetes_network_policy_v1.default_deny
  id = "${var.namespace}/crm-default-deny"
}

import {
  to = kubernetes_network_policy_v1.dns
  id = "${var.namespace}/crm-dns"
}

import {
  to = kubernetes_network_policy_v1.ingress
  id = "${var.namespace}/crm-ingress"
}

import {
  to = kubernetes_network_policy_v1.api_egress
  id = "${var.namespace}/crm-api-egress"
}

import {
  to = kubernetes_network_policy_v1.postgres_ingress
  id = "${var.namespace}/crm-postgres-ingress"
}

import {
  to = kubernetes_network_policy_v1.kafka
  id = "${var.namespace}/crm-kafka"
}

import {
  to = kubernetes_network_policy_v1.topics_egress
  id = "${var.namespace}/crm-topics-egress"
}

import {
  for_each = toset(["crm-postgres", "crm-kafka"])
  to       = kubernetes_persistent_volume_claim_v1.data[each.key]
  id       = "${var.namespace}/data-${each.key}-0"
}
