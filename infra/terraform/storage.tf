resource "kubernetes_persistent_volume_claim_v1" "data" {
  for_each = toset(["crm-postgres", "crm-kafka"])

  metadata {
    name      = "data-${each.key}-0"
    namespace = var.namespace
    labels    = local.app[each.key]
  }
  spec {
    access_modes       = ["ReadWriteOnce"]
    storage_class_name = var.storage_class
    resources {
      requests = { storage = "2Gi" }
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "kubernetes_persistent_volume_claim_v1" "backup" {
  metadata {
    name      = "crm-backup"
    namespace = var.namespace
    labels    = local.app["crm-db-backup"]
  }
  spec {
    access_modes       = ["ReadWriteOnce"]
    storage_class_name = var.storage_class
    resources {
      requests = { storage = "1Gi" }
    }
  }
  wait_until_bound = false

  lifecycle {
    prevent_destroy = true
  }
}
