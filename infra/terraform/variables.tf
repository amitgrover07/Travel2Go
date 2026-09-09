variable "project_id" {
  type        = string
  description = "GCP project ID"
  default     = "travel2go-495007"
}

variable "region" {
  type        = string
  description = "Cloud Run region"
  default     = "asia-south2"
}

variable "gcs_bucket_name" {
  type        = string
  description = "Main assets/media bucket (env GCP_BUCKET_NAME). Set to your real bucket name."
}

variable "gcs_email_bucket_name" {
  type        = string
  description = "Email-templates bucket used by notification-service (env GCP_EMAIL_BUCKET_NAME)."
}

variable "deployer_member" {
  type        = string
  description = <<-EOT
    The CI principal that runs backend-deploy.yml (the identity behind GCP_CREDENTIALS),
    e.g. "serviceAccount:github-deployer@travel2go-495007.iam.gserviceaccount.com".
    It is granted roles/iam.serviceAccountUser on each runtime SA so the workflow may
    deploy a service "as" that SA (--service-account). Leave empty to skip (then grant
    actAs manually before the first scoped deploy).
  EOT
  default     = ""
}

variable "payment_db_password" {
  type        = string
  description = "Password for the t2g_payments Postgres app user. Provide via TF_VAR_payment_db_password or a tfvars file that is NOT committed."
  sensitive   = true
}
