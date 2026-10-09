locals {
  part_of = { "app.kubernetes.io/part-of" = "bean-brigade" }
  app = {
    for name in ["crm-api", "crm-postgres", "crm-kafka", "crm-kafka-topics", "crm-db-backup"] :
    name => merge(local.part_of, { "app.kubernetes.io/name" = name })
  }
}
