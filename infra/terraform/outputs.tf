output "volume_claims" {
  value = sort([for pvc in kubernetes_persistent_volume_claim_v1.data : pvc.metadata[0].name])
}
