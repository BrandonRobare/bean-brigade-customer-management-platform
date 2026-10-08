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
