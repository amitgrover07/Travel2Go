# Least-privilege roles per runtime SA.
#
# IMPORTANT: the default compute SA that these replace carried logging + metrics
# (and broad editor-ish access) implicitly. Custom SAs start with nothing, so we
# must grant the baseline explicitly or Cloud Run loses logs/metrics.

# --- Baseline: every runtime SA writes its own logs + metrics ---
resource "google_project_iam_member" "logging" {
  for_each = google_service_account.svc
  project  = var.project_id
  role     = "roles/logging.logWriter"
  member   = "serviceAccount:${each.value.email}"
}

resource "google_project_iam_member" "metrics" {
  for_each = google_service_account.svc
  project  = var.project_id
  role     = "roles/monitoring.metricWriter"
  member   = "serviceAccount:${each.value.email}"
}

# --- Firestore (Datastore-mode roles) for the data-touching services ---
# Gateway does not read data; it only routes/invokes.
locals {
  firestore_services = [
    "identity", "package", "booking", "media",
    "notification", "trip", "payment", "reactive",
  ]
}

resource "google_project_iam_member" "firestore" {
  for_each = toset(local.firestore_services)
  project  = var.project_id
  role     = "roles/datastore.user"
  member   = "serviceAccount:${google_service_account.svc[each.key].email}"
}

# --- Cloud Storage: bucket-scoped, not project-wide ---
# media writes uploads; the others only read from the main bucket.
locals {
  bucket_readers = ["identity", "package", "booking", "notification"]
}

resource "google_storage_bucket_iam_member" "bucket_readers" {
  for_each = toset(local.bucket_readers)
  bucket   = var.gcs_bucket_name
  role     = "roles/storage.objectViewer"
  member   = "serviceAccount:${google_service_account.svc[each.key].email}"
}

resource "google_storage_bucket_iam_member" "media_writer" {
  bucket = var.gcs_bucket_name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.svc["media"].email}"
}

resource "google_storage_bucket_iam_member" "notification_email_bucket" {
  bucket = var.gcs_email_bucket_name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.svc["notification"].email}"
}

# --- Let the CI deployer act as each runtime SA ---
# Deploying a service with --service-account=X requires the deployer to hold
# roles/iam.serviceAccountUser on X, or the deploy fails with a permission error.
resource "google_service_account_iam_member" "deployer_act_as" {
  for_each           = var.deployer_member == "" ? {} : google_service_account.svc
  service_account_id = each.value.name
  role               = "roles/iam.serviceAccountUser"
  member             = var.deployer_member
}
