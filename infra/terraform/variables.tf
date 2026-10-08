variable "namespace" {
  type        = string
  description = "Namespace that runs the CRM workloads."
  default     = "student08"
}

variable "ingress_namespace" {
  type        = string
  description = "Namespace of the ingress controller allowed to reach crm-api and crm-ui."

  validation {
    condition     = length(var.ingress_namespace) > 0 && var.ingress_namespace != var.namespace
    error_message = "Set ingress_namespace to the ingress controller's namespace, not the application namespace."
  }
}

variable "storage_class" {
  type        = string
  description = "StorageClass of the PostgreSQL and Kafka volumes."

  validation {
    condition     = length(var.storage_class) > 0
    error_message = "Set storage_class to the cluster's StorageClass."
  }
}
